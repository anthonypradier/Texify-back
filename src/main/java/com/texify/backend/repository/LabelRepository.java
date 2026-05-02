package com.texify.backend.repository;

import com.texify.backend.entity.Label;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabelRepository extends JpaRepository<Label, Long> {
    List<Label> findByUserEmail(String userEmail);
    Optional<Label> findByIdAndUserEmail(Long id, String userEmail);
}
