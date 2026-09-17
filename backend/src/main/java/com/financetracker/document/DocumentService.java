package com.financetracker.document;

import com.financetracker.document.extraction.ExtractionPipeline;
import com.financetracker.document.extraction.ExtractionResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Main Responsibility: Validate uploads, run extraction, and serve owner-scoped documents.
 *
 * Owns file rules, storage write order, status transitions (UPLOADED → PROCESSING →
 * REVIEW_REQUIRED / PROCESSING_FAILED), pending inbox list, review GET / file stream /
 * pending DELETE, and cleanup when the DB save fails after the file is already on disk.
 * Extraction itself is delegated to ExtractionPipeline; this class maps results to
 * document_extractions and never creates expenses.
 */
@Service
public class DocumentService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "application/pdf"
    );
    private static final int MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final String STATUS_UPLOADED = "UPLOADED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    private static final String STATUS_PROCESSING_FAILED = "PROCESSING_FAILED";
    private static final String STATUS_SAVED = "SAVED";

    private final DocumentRepository documentRepository;
    private final DocumentExtractionRepository documentExtractionRepository;
    private final FileStorageService fileStorageService;
    private final ExtractionPipeline extractionPipeline;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentExtractionRepository documentExtractionRepository,
            FileStorageService fileStorageService,
            ExtractionPipeline extractionPipeline
    ) {
        this.documentRepository = documentRepository;
        this.documentExtractionRepository = documentExtractionRepository;
        this.fileStorageService = fileStorageService;
        this.extractionPipeline = extractionPipeline;
    }

    /**
     * Validate the multipart file, store it on disk, create the DB row, then run extraction
     * in the same request so the response status is already post-processing.
     */
    public DocumentReviewResponse uploadDocument(Long userId, MultipartFile file) {
        validateFile(file);

        String storagePath = fileStorageService.storeFile(userId, file);

        Document savedDocument;
        try {
            Document document = new Document();
            document.setUserId(userId);
            document.setStatus(STATUS_UPLOADED);
            document.setStoragePath(storagePath);
            document.setOriginalFilename(resolveOriginalFilename(file));
            document.setMimeType(file.getContentType());
            document.setFileSizeBytes(Math.toIntExact(file.getSize()));

            savedDocument = documentRepository.save(document);
        } catch (RuntimeException exception) {
            // Only safe place to delete the file: no documents row was persisted yet.
            try {
                fileStorageService.deleteStoredFile(storagePath);
            } catch (RuntimeException cleanupException) {
                // Keep the DB/storage failure as the main cause, but do not lose cleanup details.
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }

        // File + UPLOADED row exist. Never delete the file from here — prefer a recoverable
        // PROCESSING_FAILED document (and always return its id) over a silent disk orphan.
        try {
            runExtraction(savedDocument);
        } catch (RuntimeException processingException) {
            savedDocument = recoverAfterProcessingFailure(savedDocument, processingException);
        }
        return toReviewResponse(savedDocument);
    }

    /**
     * Re-run extraction. Allowed only from UPLOADED or PROCESSING_FAILED.
     * Wrong owner → 404; illegal status → 409.
     */
    public DocumentReviewResponse process(Long userId, Long documentId) {
        Document document = findOwnedDocument(userId, documentId);
        String status = document.getStatus();

        if (!STATUS_UPLOADED.equals(status) && !STATUS_PROCESSING_FAILED.equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Document can only be processed from UPLOADED or PROCESSING_FAILED"
            );
        }

        try {
            runExtraction(document);
        } catch (RuntimeException processingException) {
            document = recoverAfterProcessingFailure(document, processingException);
        }
        return toReviewResponse(document);
    }

    /**
     * From PROCESSING_FAILED: create/clear an empty extraction row and move to REVIEW_REQUIRED
     * so the user can fill the review form by hand.
     */
    public DocumentReviewResponse continueManual(Long userId, Long documentId) {
        Document document = findOwnedDocument(userId, documentId);

        if (!STATUS_PROCESSING_FAILED.equals(document.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Document can only continue manually from PROCESSING_FAILED"
            );
        }

        DocumentExtraction extraction = documentExtractionRepository
                .findByDocumentId(document.getId())
                .orElseGet(() -> {
                    DocumentExtraction created = new DocumentExtraction();
                    created.setDocumentId(document.getId());
                    return created;
                });
        clearProposedFields(extraction);
        documentExtractionRepository.save(extraction);

        document.setStatus(STATUS_REVIEW_REQUIRED);
        documentRepository.save(document);

        return toReviewResponse(document);
    }

    /**
     * Pending inbox: owned documents with status ≠ SAVED, newest first.
     * Slim rows only (no extraction). Empty list when nothing is pending.
     */
    public List<DocumentResponse> listPending(Long userId) {
        return documentRepository.findPendingByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toListItemResponse)
                .toList();
    }

    /**
     * Owner-scoped review payload (metadata + fileUrl + nullable extraction).
     * Foreign or missing id → 404. Does not expose storagePath.
     */
    public DocumentReviewResponse getDocument(Long userId, Long documentId) {
        Document document = findOwnedDocument(userId, documentId);
        return toReviewResponse(document);
    }

    /**
     * Resolve the stored file for an owned document so the controller can stream bytes.
     * Missing row, foreign owner, or missing disk file → 404.
     */
    public DocumentFile getDocumentFile(Long userId, Long documentId) {
        Document document = findOwnedDocument(userId, documentId);

        Path filePath;
        try {
            filePath = fileStorageService.readStoredFile(document.getStoragePath());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document file not found", exception);
        }

        return new DocumentFile(filePath, document.getMimeType(), document.getOriginalFilename());
    }

    /**
     * Hard-delete a pending document (DB row cascades extraction) then remove the disk file.
     * SAVED is blocked (409) because expenses use ON DELETE RESTRICT on documents.
     * Foreign or missing → 404.
     */
    public void deleteDocument(Long userId, Long documentId) {
        Document document = findOwnedDocument(userId, documentId);

        if (STATUS_SAVED.equals(document.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Saved documents cannot be deleted"
            );
        }

        String storagePath = document.getStoragePath();
        // Delete the DB row first so a failed disk cleanup cannot leave a usable orphan document.
        documentRepository.delete(document);
        fileStorageService.deleteStoredFile(storagePath);
    }

    /**
     * Set PROCESSING, run ExtractionPipeline on the stored file, then REVIEW_REQUIRED
     * when any header is usable, otherwise PROCESSING_FAILED.
     * Hard pipeline failures (OCR/LLM down, missing collaborators) also end as PROCESSING_FAILED.
     */
    private void runExtraction(Document document) {
        try {
            document.setStatus(STATUS_PROCESSING);
            documentRepository.save(document);

            Path storedFile = fileStorageService.readStoredFile(document.getStoragePath());
            ExtractionResult result = extractionPipeline.extract(storedFile, document.getMimeType());

            if (!result.hasUsableHeader()) {
                clearExtractionIfPresent(document.getId());
                document.setStatus(STATUS_PROCESSING_FAILED);
                documentRepository.save(document);
                return;
            }

            DocumentExtraction extraction = documentExtractionRepository
                    .findByDocumentId(document.getId())
                    .orElseGet(() -> {
                        DocumentExtraction created = new DocumentExtraction();
                        created.setDocumentId(document.getId());
                        return created;
                    });
            applyExtractionResult(extraction, result);
            documentExtractionRepository.save(extraction);

            document.setStatus(STATUS_REVIEW_REQUIRED);
            documentRepository.save(document);
        } catch (RuntimeException exception) {
            // Prefer a recoverable PROCESSING_FAILED row over failing the whole upload/retry
            // (includes ExtractionException, missing file, and unexpected errors).
            try {
                markProcessingFailed(document);
            } catch (RuntimeException saveFailed) {
                exception.addSuppressed(saveFailed);
                throw exception;
            }
        }
    }

    /** Copy validated header fields onto the extraction entity (line items ignored). */
    private static void applyExtractionResult(DocumentExtraction extraction, ExtractionResult result) {
        extraction.setRawOcrText(result.rawOcrText());
        extraction.setProposedMerchant(result.merchant());
        extraction.setProposedDate(result.date());
        extraction.setProposedAmount(result.totalAmount());
        extraction.setProposedCurrency(result.currency());
        extraction.setProposedCategoryId(result.categoryId());
    }

    /** Clear all proposed fields (manual-continue empty form / failed cleanup). */
    private static void clearProposedFields(DocumentExtraction extraction) {
        extraction.setRawOcrText(null);
        extraction.setProposedMerchant(null);
        extraction.setProposedDate(null);
        extraction.setProposedAmount(null);
        extraction.setProposedCurrency(null);
        extraction.setProposedCategoryId(null);
    }

    /**
     * Last resort after UPLOADED already exists: persist PROCESSING_FAILED if possible,
     * otherwise reload the durable row so the client still receives a truthful document id/status.
     * Does not delete the stored file (row would point at a missing path).
     */
    private Document recoverAfterProcessingFailure(Document document, RuntimeException processingException) {
        try {
            markProcessingFailed(document);
            return document;
        } catch (RuntimeException saveFailed) {
            processingException.addSuppressed(saveFailed);
            return documentRepository.findById(document.getId()).orElse(document);
        }
    }

    private void markProcessingFailed(Document document) {
        document.setStatus(STATUS_PROCESSING_FAILED);
        documentRepository.save(document);
    }

    private void clearExtractionIfPresent(Long documentId) {
        documentExtractionRepository.findByDocumentId(documentId).ifPresent(extraction -> {
            clearProposedFields(extraction);
            documentExtractionRepository.save(extraction);
        });
    }

    private Document findOwnedDocument(Long userId, Long documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be 5 MB or smaller");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type");
        }
    }

    /**
     * Keep a safe fallback name so DB constraints still hold if the client omits the filename.
     */
    private String resolveOriginalFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return "uploaded-file";
        }
        return originalFilename.trim();
    }

    private DocumentResponse toListItemResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getStatus(),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.getCreatedAt(),
                fileUrlFor(document.getId())
        );
    }

    private DocumentReviewResponse toReviewResponse(Document document) {
        ExtractionResponse extraction = documentExtractionRepository
                .findByDocumentId(document.getId())
                .map(this::toExtractionResponse)
                .orElse(null);

        return new DocumentReviewResponse(
                document.getId(),
                document.getStatus(),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.getFileSizeBytes(),
                document.getCreatedAt(),
                fileUrlFor(document.getId()),
                extraction
        );
    }

    /** Authenticated file-stream path; never the server storagePath. */
    private static String fileUrlFor(Long documentId) {
        return "/documents/" + documentId + "/file";
    }

    private ExtractionResponse toExtractionResponse(DocumentExtraction extraction) {
        return new ExtractionResponse(
                extraction.getRawOcrText(),
                extraction.getProposedMerchant(),
                extraction.getProposedDate(),
                extraction.getProposedAmount(),
                extraction.getProposedCurrency(),
                extraction.getProposedCategoryId()
        );
    }

    /**
     * Internal payload for streaming: absolute path plus response headers (MIME + filename).
     * Not a public API DTO — never includes storagePath as a client-facing field.
     */
    public record DocumentFile(Path path, String mimeType, String originalFilename) {
    }
}
