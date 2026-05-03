package com.texify.backend.repository;

import com.texify.backend.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByOwnerEmailAndDeletedAtIsNull(String ownerEmail);
    Optional<Document> findByIdAndOwnerEmailAndDeletedAtIsNull(Long id, String ownerEmail);
    List<Document> findByOwnerEmailAndDeletedAtIsNotNull(String ownerEmail);
    Optional<Document> findByIdAndOwnerEmail(Long id, String ownerEmail);
    List<Document> findByDeletedAtBefore(LocalDateTime cutoff);
}
