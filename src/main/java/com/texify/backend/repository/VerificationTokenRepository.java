package com.texify.backend.repository;

import com.texify.backend.entity.TokenType;
import com.texify.backend.entity.User;
import com.texify.backend.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByToken(String token);

    /** Deletes all tokens of a given type for a user — called before issuing a replacement. */
    void deleteByUserAndType(User user, TokenType type);
}
