package com.texify.backend.service;

import com.texify.backend.entity.WaitlistEntry;
import com.texify.backend.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures waitlist email sign-ups from the public landing page.
 * <p>
 * Idempotent by design: a brand-new email and an already-registered one are
 * treated identically (no error surfaced, existence never leaked). The DB
 * UNIQUE constraint is the final guard against concurrent duplicate inserts.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;

    /**
     * Adds an email to the waitlist, ignoring duplicates.
     *
     * @param rawEmail the email as submitted (will be trimmed + lower-cased)
     */
    @Transactional
    public void subscribe(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();

        if (waitlistRepository.existsByEmail(email)) {
            log.debug("Waitlist: email already registered");
            return;
        }

        try {
            waitlistRepository.save(WaitlistEntry.builder().email(email).build());
            log.info("Waitlist: new email registered");
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert won the race — the UNIQUE constraint rejected ours.
            // Idempotent outcome: treat as already registered.
            log.debug("Waitlist: concurrent duplicate ignored");
        }
    }
}
