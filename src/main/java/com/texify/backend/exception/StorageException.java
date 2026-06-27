package com.texify.backend.exception;

/**
 * Thrown when a file cannot be stored, loaded, or deleted from the storage backend.
 * Mapped to {@code 500 Internal Server Error} by {@link GlobalExceptionHandler}.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
