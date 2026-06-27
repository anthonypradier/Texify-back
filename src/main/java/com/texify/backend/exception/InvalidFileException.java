package com.texify.backend.exception;

/**
 * Thrown when an uploaded file fails validation (empty, wrong type, too large).
 * Mapped to {@code 400 Bad Request} by {@link GlobalExceptionHandler}.
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}
