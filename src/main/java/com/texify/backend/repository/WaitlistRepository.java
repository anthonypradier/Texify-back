package com.texify.backend.repository;

import com.texify.backend.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    boolean existsByEmail(String email);
}
