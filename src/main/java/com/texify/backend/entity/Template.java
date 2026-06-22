package com.texify.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing a document template.
 * <p>
 * A template is a reusable starting point that a user can preview in the editor
 * and turn into a brand-new document. MVP templates are <em>system</em>
 * templates: they are seeded at startup and have no creator
 * ({@code createdBy == null}, {@code isSystem == true}). User-created templates
 * are planned for a later iteration.
 * </p>
 */
@Entity
@Table(name = "templates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    /** Editor block content of the template (kept for future use / rich preview). */
    @Column(columnDefinition = "JSON")
    @Builder.Default
    private String blocks = "[]";

    /** Path to the default compiled PDF shown as a preview in the editor. */
    @Column(name = "preview_pdf_path")
    private String previewPdfPath;

    private String icon;

    private String color;

    /** Free-form grouping label (e.g. "Academic", "Career"). */
    private String category;

    /** {@code true} for built-in templates available to every user. */
    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean isSystem = false;

    /**
     * The user who created this template, or {@code null} for system templates.
     * Mapped lazily — never expose the entity directly in an HTTP response.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
