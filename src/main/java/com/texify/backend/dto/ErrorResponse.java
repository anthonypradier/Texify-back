package com.texify.backend.dto;

import java.time.LocalDateTime;

/**
 * Standardised error payload returned by the global exception handler.
 * <p>
 * A consistent structure lets the frontend parse errors uniformly
 * regardless of the specific exception thrown on the server.
 * </p>
 *
 * @param status    HTTP status code (mirrors the HTTP response status)
 * @param error     short human-readable label for the error type
 * @param message   detailed message explaining the problem
 * @param timestamp moment at which the error occurred (server time)
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timestamp
) {
}
