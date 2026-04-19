package com.texify.backend.repository;

import com.texify.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 * <p>
 * Provides CRUD operations plus the email-based lookups required by
 * the authentication and registration flows.
 * </p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their email address.
     *
     * @param email the email to search for
     * @return an {@link Optional} containing the user, or empty if not found
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks whether an account with the given email already exists.
     * <p>
     * Preferred over {@link #findByEmail} during registration because it
     * avoids loading the full entity when only existence matters.
     * </p>
     *
     * @param email the email to check
     * @return {@code true} if at least one user has this email
     */
    boolean existsByEmail(String email);
}
