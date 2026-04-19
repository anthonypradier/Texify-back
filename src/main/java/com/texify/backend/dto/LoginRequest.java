package com.texify.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request payload for the login endpoint.
 * <p>
 * After successful authentication the server returns a signed JWT that must
 * be attached to every subsequent request as {@code Authorization: Bearer <token>}.
 * </p>
 */
@Data
public class LoginRequest {

    /** Email address used as the login identifier. */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    /** Plain-text password compared against the stored BCrypt hash. */
    @NotBlank(message = "Password is required")
    private String password;
}
