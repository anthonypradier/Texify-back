package com.texify.backend.dto;

/**
 * Response payload returned after a successful login or registration.
 * <p>
 * The {@code token} is a signed JWT that the client must store and attach
 * to every protected request via {@code Authorization: Bearer <token>}.
 * It expires after the duration configured in {@code jwt.expiration}.
 * </p>
 *
 * @param token     signed JWT access token
 * @param email     authenticated user's email
 * @param firstName authenticated user's first name
 * @param lastName  authenticated user's last name
 * @param role      granted authority (e.g. {@code ROLE_USER})
 */
public record AuthResponse(
        String token,
        String email,
        String firstName,
        String lastName,
        String role
) {
}
