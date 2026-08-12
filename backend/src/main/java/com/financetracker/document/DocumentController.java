package com.financetracker.document;

import com.financetracker.security.CurrentUser;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * Main Responsibility: Expose authenticated document upload, processing, review, file, and delete APIs.
 *
 * The current user always comes from JWT auth, never from request input, so each request
 * is safely scoped to its owner. Processing and ownership rules live in DocumentService.
 */
@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUser currentUser;

    public DocumentController(DocumentService documentService, CurrentUser currentUser) {
        this.documentService = documentService;
        this.currentUser = currentUser;
    }

    /**
     * Accept a multipart upload, run sync mock processing, and return post-processing review data.
     */
    @PostMapping
    public ResponseEntity<DocumentReviewResponse> upload(@RequestPart("file") MultipartFile file) {
        DocumentReviewResponse response = documentService.uploadDocument(currentUser.getUserId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Re-run mock processing from UPLOADED or PROCESSING_FAILED. */
    @PostMapping("/{id}/process")
    public DocumentReviewResponse process(@PathVariable Long id) {
        return documentService.process(currentUser.getUserId(), id);
    }

    /** Move a failed document to an empty review form (REVIEW_REQUIRED). */
    @PostMapping("/{id}/continue-manual")
    public DocumentReviewResponse continueManual(@PathVariable Long id) {
        return documentService.continueManual(currentUser.getUserId(), id);
    }

    /**
     * Owner-scoped review DTO (metadata + fileUrl + nullable extraction).
     * Missing or foreign ids both resolve as 404.
     */
    @GetMapping("/{id}")
    public DocumentReviewResponse getById(@PathVariable Long id) {
        return documentService.getDocument(currentUser.getUserId(), id);
    }

    /**
     * Stream stored file bytes for the owner. Content-Type from mime_type; inline disposition.
     * Missing or foreign ids (or missing disk file) → 404.
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> getFile(@PathVariable Long id) {
        DocumentService.DocumentFile documentFile = documentService.getDocumentFile(currentUser.getUserId(), id);

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(documentFile.originalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documentFile.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(documentFile.path()));
    }

    /**
     * Hard-delete a pending document (cascade extraction) and its disk file.
     * SAVED → 409; missing or foreign → 404.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.deleteDocument(currentUser.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
