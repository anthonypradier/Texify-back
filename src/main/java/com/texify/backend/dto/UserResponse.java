package com.texify.backend.dto;

/**
 * Public representation of a user — safe to expose over the API.
 * <p>
 * Never includes the hashed password or any other sensitive field.
 * Returned by endpoints such as {@code GET /api/auth/me}.
 * </p>
 *
 * @param id        unique database identifier
 * @param email     user's email address
 * @param firstName user's first name
 * @param lastName  user's last name
 * @param role      granted authority (e.g. {@code ROLE_USER})
 */
public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String role
) {
}
