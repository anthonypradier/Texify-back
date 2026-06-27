package com.texify.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the {@code texify.storage.*} properties.
 * <p>
 * In dev, files live under {@link #localRootPath} and are served by
 * {@code StorageController} at {@code /storage/**}. In prod, a CDN / object
 * store (S3, Cloudflare R2) takes over — only {@link #type} and {@link #baseUrl}
 * change, the application code stays the same.
 * </p>
 */
@Configuration
@ConfigurationProperties(prefix = "texify.storage")
@Data
public class StorageConfig {

    /** Root directory of the local storage (resolved to an absolute path at runtime). */
    private String localRootPath = "./storage";

    /** Active storage backend. */
    private StorageType type = StorageType.LOCAL;

    /**
     * Base URL used to build public file URLs.
     * Dev: {@code http://localhost:9000/storage}; prod: a CDN host.
     */
    private String baseUrl = "http://localhost:9000/storage";

    public enum StorageType { LOCAL, S3 }
}
