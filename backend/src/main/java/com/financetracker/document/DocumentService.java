package com.financetracker.document;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Main Responsibility: Validate uploads, save document rows, and load owner-scoped metadata.
 *
 * Keeps the controller thin by owning file rules, storage write order, and cleanup when
 * the database save fails after the file is already on disk.
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

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;

    public DocumentService(DocumentRepository documentRepository, FileStorageService fileStorageService) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Validate the multipart file, store it on disk, then create the DB row.
     */
    public DocumentResponse uploadDocument(Long userId, MultipartFile file) {
        validateFile(file);

        String storagePath = fileStorageService.storeFile(userId, file);

        try {
            Document document = new Document();
            document.setUserId(userId);
            document.setStatus(STATUS_UPLOADED);
            document.setStoragePath(storagePath);
            document.setOriginalFilename(resolveOriginalFilename(file));
            document.setMimeType(file.getContentType());
            document.setFileSizeBytes(Math.toIntExact(file.getSize()));

            Document savedDocument = documentRepository.save(document);
            return toResponse(savedDocument);
        } catch (RuntimeException exception) {
            try {
                fileStorageService.deleteStoredFile(storagePath);
            } catch (RuntimeException cleanupException) {
                // Keep the DB/storage failure as the main cause, but do not lose cleanup details.
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    /**
     * Use id + userId so foreign document ids behave like missing records.
     */
    public DocumentResponse getDocument(Long userId, Long documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        return toResponse(document);
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
}
