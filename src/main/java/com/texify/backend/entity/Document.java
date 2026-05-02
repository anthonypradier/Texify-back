package com.texify.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "JSON")
    @Builder.Default
    private String blocks = "[]";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = false;

    @Column(name = "compile_pdf_path")
    private String compilePdfPath;

    @Column(name = "word_count", nullable = false)
    @Builder.Default
    private int wordCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean pinned = false;

    private String icon;

    private String color;

    @Column(name = "equation_count", nullable = false)
    @Builder.Default
    private int equationCount = 0;

    @Column(name = "figure_count", nullable = false)
    @Builder.Default
    private int figureCount = 0;

    @Column(name = "plot_count", nullable = false)
    @Builder.Default
    private int plotCount = 0;

    @Column(name = "code_count", nullable = false)
    @Builder.Default
    private int codeCount = 0;

    @Column(name = "compilation_count", nullable = false)
    @Builder.Default
    private int compilationCount = 0;

    @ManyToMany(mappedBy = "documents", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Label> labels = new ArrayList<>();

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
