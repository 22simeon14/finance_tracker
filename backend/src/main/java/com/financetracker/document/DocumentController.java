package com.financetracker.document;

import com.financetracker.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Main Responsibility: Expose authenticated upload, processing, and document-metadata endpoints.
 *
 * The current user always comes from JWT auth, never from request input, so each request
 * is safely scoped to its owner. Processing rules live in DocumentService.
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

    /** Return metadata only; missing or foreign ids both resolve as 404. */
    @GetMapping("/{id}")
    public DocumentResponse getById(@PathVariable Long id) {
        return documentService.getDocument(currentUser.getUserId(), id);
    }
}
