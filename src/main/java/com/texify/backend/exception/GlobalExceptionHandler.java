package com.texify.backend.exception;

import com.texify.backend.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for all REST controllers.
 * <p>
 * Converts exceptions into a consistent {@link ErrorResponse} JSON structure
 * so the frontend can parse errors uniformly. Only the minimum amount of
 * information necessary is exposed to avoid leaking implementation details.
 * </p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles duplicate-email conflicts during registration.
     *
     * @param ex the exception carrying the conflicting email
     * @return 409 Conflict
     */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        log.warn("Registration conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    /**
     * Handles invalid credentials submitted during login.
     * <p>
     * Returns a generic message on purpose — never specify which field is wrong.
     * </p>
     *
     * @param ex the Spring Security bad-credentials exception
     * @return 401 Unauthorized
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Failed login attempt: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password");
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex) {
        log.warn("Login attempt on unverified account");
        return build(HttpStatus.FORBIDDEN, "Forbidden",
                "Please verify your email address before logging in.");
    }

    @ExceptionHandler(InvalidVerificationTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidVerificationToken(
            InvalidVerificationTokenException ex) {
        log.warn("Invalid verification token: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    /**
     * Handles the case where no account exists for a given email.
     *
     * @param ex the exception thrown by the {@code UserDetailsService}
     * @return 404 Not Found
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(UsernameNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    /**
     * Handles {@code @Valid} constraint violations on request bodies.
     * <p>
     * Concatenates all field-level errors so the frontend receives every
     * violation in a single response.
     * </p>
     *
     * @param ex the validation exception containing field errors
     * @return 400 Bad Request with all violation messages joined by {@code "; "}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", details);
        return build(HttpStatus.BAD_REQUEST, "Validation Error", details);
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * <p>
     * Logs the full stack trace but returns only a generic message to avoid
     * leaking internal details to the client.
     * </p>
     *
     * @param ex the unexpected exception
     * @return 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Please try again later.");
    }

    /**
     * Builds a {@link ResponseEntity} wrapping an {@link ErrorResponse}.
     *
     * @param status  the HTTP status to set on the response
     * @param error   short error label
     * @param message detailed error message
     * @return the assembled response entity
     */
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), error, message, LocalDateTime.now()));
    }
}
