package com.financetracker.document;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

/**
 * Main Responsibility: Validate uploads, run mock processing, and load owner-scoped documents.
 *
 * Owns file rules, storage write order, status transitions (UPLOADED → PROCESSING →
 * REVIEW_REQUIRED / PROCESSING_FAILED), and cleanup when the DB save fails after the file
 * is already on disk. Keeps the controller thin.
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

    private final DocumentRepository documentRepository;
    private final DocumentExtractionRepository documentExtractionRepository;
    private final FileStorageService fileStorageService;
    private final MockExtractionService mockExtractionService;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentExtractionRepository documentExtractionRepository,
            FileStorageService fileStorageService,
            MockExtractionService mockExtractionService
    ) {
        this.documentRepository = documentRepository;
        this.documentExtractionRepository = documentExtractionRepository;
        this.fileStorageService = fileStorageService;
        this.mockExtractionService = mockExtractionService;
    }

    /**
     * Validate the multipart file, store it on disk, create the DB row, then run mock processing
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
            try {
                fileStorageService.deleteStoredFile(storagePath);
            } catch (RuntimeException cleanupException) {
                // Keep the DB/storage failure as the main cause, but do not lose cleanup details.
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }

        // File + UPLOADED row exist; prefer PROCESSING_FAILED over an orphaned disk file if mock work fails.
        runMockProcessing(savedDocument);
        return toReviewResponse(savedDocument);
    }

    /**
     * Re-run mock processing. Allowed only from UPLOADED or PROCESSING_FAILED.
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

        runMockProcessing(document);
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
        mockExtractionService.clearProposedFields(extraction);
        documentExtractionRepository.save(extraction);

        document.setStatus(STATUS_REVIEW_REQUIRED);
        documentRepository.save(document);

        return toReviewResponse(document);
    }

    /**
     * Use id + userId so foreign document ids behave like missing records.
     */
    public DocumentResponse getDocument(Long userId, Long documentId) {
        Document document = findOwnedDocument(userId, documentId);
        return toResponse(document);
    }

    /**
     * Set PROCESSING, then either fail (filename contains "fail") or upsert mock extraction
     * and set REVIEW_REQUIRED. Unexpected errors mark PROCESSING_FAILED so the row stays recoverable.
     */
    private void runMockProcessing(Document document) {
        document.setStatus(STATUS_PROCESSING);
        documentRepository.save(document);

        try {
            if (shouldSimulateFailure(document.getOriginalFilename())) {
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
            mockExtractionService.applyMockProposal(extraction);
            documentExtractionRepository.save(extraction);

            document.setStatus(STATUS_REVIEW_REQUIRED);
            documentRepository.save(document);
        } catch (RuntimeException exception) {
            // Prefer a recoverable PROCESSING_FAILED row over failing the whole upload/retry.
            try {
                document.setStatus(STATUS_PROCESSING_FAILED);
                documentRepository.save(document);
            } catch (RuntimeException saveFailed) {
                exception.addSuppressed(saveFailed);
                throw exception;
            }
        }
    }

    /** Filename marker for local testing of the failed-processing path (case-insensitive). */
    private static boolean shouldSimulateFailure(String originalFilename) {
        if (originalFilename == null) {
            return false;
        }
        return originalFilename.toLowerCase(Locale.ROOT).contains("fail");
    }

    private void clearExtractionIfPresent(Long documentId) {
        documentExtractionRepository.findByDocumentId(documentId).ifPresent(extraction -> {
            mockExtractionService.clearProposedFields(extraction);
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

    private DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getStatus(),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.getFileSizeBytes(),
                document.getCreatedAt()
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
                "/documents/" + document.getId() + "/file",
                extraction
        );
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
}
