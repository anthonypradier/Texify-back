package com.texify.backend.entity;

/**
 * Roles available to a Texify user.
 * <p>
 * The {@code ROLE_} prefix satisfies Spring Security's default authority-matching
 * convention used by {@code hasRole()} and similar expressions.
 * </p>
 */
public enum Role {

    /** Standard authenticated user. */
    ROLE_USER,

    /** Administrator with elevated access. */
    ROLE_ADMIN
}
