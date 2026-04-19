package com.texify.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request payload for the account registration endpoint.
 * <p>
 * All constraints are enforced by Bean Validation before the service layer
 * is reached. The plain-text password is BCrypt-hashed in the service.
 * </p>
 */
@Data
public class RegisterRequest {

    /** Must be a syntactically valid email address. */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    /**
     * Plain-text password submitted by the user.
     * Minimum 8 characters; hashed before persistence.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;
}
