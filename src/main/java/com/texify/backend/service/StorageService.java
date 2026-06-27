package com.texify.backend.service;

import com.texify.backend.config.StorageConfig;
import com.texify.backend.exception.StorageException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local-filesystem implementation of the file storage abstraction.
 * <p>
 * Stores files under {@link StorageConfig#getLocalRootPath()} and exposes them
 * through relative paths (never absolute). Path traversal is rejected so a
 * crafted {@code relativePath} can never escape the storage root.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final StorageConfig storageConfig;

    /** Creates the base directory layout at startup. */
    @PostConstruct
    public void init() {
        createDirectoryIfNotExists("templates/previews");
        createDirectoryIfNotExists("templates/pdfs");
        createDirectoryIfNotExists("documents/pdfs");
        createDirectoryIfNotExists("documents/images");
        log.info("Storage initialized at: {}",
                Paths.get(storageConfig.getLocalRootPath()).toAbsolutePath().normalize());
    }

    /**
     * Stores a stream at the given relative path (parent dirs are created).
     *
     * @return the relative path actually used
     */
    public String store(InputStream inputStream, String relativePath) throws IOException {
        Path destination = resolveAndCreateParents(relativePath);
        Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Stored file at: {}", destination);
        return relativePath;
    }

    /**
     * Stores a byte array at the given relative path (parent dirs are created).
     *
     * @return the relative path actually used
     */
    public String store(byte[] bytes, String relativePath) throws IOException {
        Path destination = resolveAndCreateParents(relativePath);
        Files.write(destination, bytes);
        log.debug("Stored file at: {}", destination);
        return relativePath;
    }

    /** Loads a file as a Spring {@link Resource}. */
    public Resource load(String relativePath) {
        Path filePath = resolveRelativePath(relativePath);
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) {
            throw new StorageException("File not found: " + relativePath);
        }
        return resource;
    }

    /** Deletes a file. Never throws if the file is missing. */
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Path filePath = resolveRelativePath(relativePath);
            Files.deleteIfExists(filePath);
            log.debug("Deleted file: {}", filePath);
        } catch (IOException e) {
            log.warn("Could not delete file {}: {}", relativePath, e.getMessage());
        }
    }

    /**
     * Builds the public URL for a stored file.
     * <p>
     * A path starting with {@code "/"} is treated as an already-public absolute
     * path (e.g. a seeded static asset served from {@code src/main/resources/static})
     * and returned unchanged. Otherwise it is prefixed with the storage base URL.
     * </p>
     *
     * @return the public URL, or {@code null} if {@code relativePath} is blank
     */
    public String buildPublicUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        if (relativePath.startsWith("/")) {
            return relativePath;
        }
        return storageConfig.getBaseUrl() + "/" + relativePath;
    }

    /** Tells whether a file exists in the storage. */
    public boolean exists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        return Files.exists(resolveRelativePath(relativePath));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Path resolveRelativePath(String relativePath) {
        Path root = Paths.get(storageConfig.getLocalRootPath()).toAbsolutePath().normalize();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("Path traversal attempt detected: " + relativePath);
        }
        return resolved;
    }

    private Path resolveAndCreateParents(String relativePath) throws IOException {
        Path destination = resolveRelativePath(relativePath);
        Files.createDirectories(destination.getParent());
        return destination;
    }

    private void createDirectoryIfNotExists(String relativePath) {
        try {
            Files.createDirectories(resolveRelativePath(relativePath));
        } catch (IOException e) {
            log.error("Failed to create directory {}: {}", relativePath, e.getMessage());
        }
    }
}
