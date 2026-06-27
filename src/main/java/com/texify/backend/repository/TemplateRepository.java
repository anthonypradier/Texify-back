package com.texify.backend.repository;

import com.texify.backend.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TemplateRepository extends JpaRepository<Template, Long> {

    /**
     * Returns every template the given user may access: all system templates
     * plus the templates they created themselves.
     */
    @Query("SELECT t FROM Template t LEFT JOIN t.createdBy u WHERE t.isSystem = true OR u.email = :email")
    List<Template> findAccessibleBy(@Param("email") String email);

    /** Used by the seeder to avoid inserting duplicate system templates. */
    long countByIsSystemTrue();
}
