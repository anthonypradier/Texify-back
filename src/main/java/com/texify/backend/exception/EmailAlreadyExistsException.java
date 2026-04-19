package com.texify.backend.exception;

/**
 * Thrown when a registration attempt uses an email address that is already
 * associated with an existing account.
 * <p>
 * Mapped to HTTP 409 Conflict by the {@code GlobalExceptionHandler}.
 * </p>
 */
public class EmailAlreadyExistsException extends RuntimeException {

    /**
     * Constructs the exception with a message that identifies the conflicting email.
     *
     * @param email the duplicate email address
     */
    public EmailAlreadyExistsException(String email) {
        super("An account with email '" + email + "' already exists");
    }
}
