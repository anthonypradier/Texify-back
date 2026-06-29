package com.texify.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A single email captured by the public landing page waitlist.
 * <p>
 * The {@code email} column carries a UNIQUE constraint so duplicates are
 * rejected at the database level even under concurrent submissions.
 * </p>
 */
@Entity
@Table(name = "waitlist_entries", uniqueConstraints = @UniqueConstraint(name = "uk_waitlist_email", columnNames = "email"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored normalised (trimmed + lower-cased) by the service. */
    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
