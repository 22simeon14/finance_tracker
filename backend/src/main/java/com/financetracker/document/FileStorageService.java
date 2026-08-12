package com.financetracker.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Main Responsibility: Store, read, and delete document files under the configured UPLOAD_DIR.
 *
 * Keeps file paths server-controlled by using the current user id and a generated UUID name.
 * Returns only a relative storage path so the database never depends on a machine-specific absolute path.
 * All path operations reject escape attempts outside the upload root.
 */
@Service
public class FileStorageService {

    private static final Map<String, String> MIME_TO_EXTENSION = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "application/pdf", ".pdf"
    );

    private final Path uploadRoot;

    public FileStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * Store the uploaded file in a per-user folder and return the relative DB path.
     */
    public String storeFile(Long userId, MultipartFile file) {
        String extension = resolveExtension(file.getContentType());
        String storedFilename = UUID.randomUUID() + extension;
        Path userDirectory = uploadRoot.resolve(String.valueOf(userId)).normalize();
        Path targetFile = userDirectory.resolve(storedFilename).normalize();

        if (!targetFile.startsWith(uploadRoot)) {
            throw new IllegalStateException("Upload path escaped configured root.");
        }

        try {
            Files.createDirectories(userDirectory);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            deleteIfExists(targetFile);
            throw new IllegalStateException("Failed to store uploaded file.", exception);
        }

        return uploadRoot.relativize(targetFile).toString().replace('\\', '/');
    }

    /**
     * Resolve a stored file for streaming. Rejects path escape and missing files.
     */
    public Path readStoredFile(String relativePath) {
        Path targetFile = resolveUnderRoot(relativePath);

        if (!Files.isRegularFile(targetFile)) {
            throw new IllegalStateException("Stored file not found.");
        }

        return targetFile;
    }

    /**
     * Delete a previously stored file (upload rollback or pending document delete).
     */
    public void deleteStoredFile(String relativePath) {
        Path targetFile = resolveUnderRoot(relativePath);
        deleteIfExists(targetFile);
    }

    /** Normalize relativePath and ensure it stays under UPLOAD_DIR. */
    private Path resolveUnderRoot(String relativePath) {
        Path targetFile = uploadRoot.resolve(relativePath).normalize();

        if (!targetFile.startsWith(uploadRoot)) {
            throw new IllegalStateException("Stored file path escaped configured root.");
        }

        return targetFile;
    }

    private String resolveExtension(String mimeType) {
        String extension = MIME_TO_EXTENSION.get(mimeType);
        if (extension == null) {
            throw new IllegalArgumentException("Unsupported file type: " + mimeType);
        }
        return extension;
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete stored file.", exception);
        }
    }
}
