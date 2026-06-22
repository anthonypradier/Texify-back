package com.texify.backend.config;

import com.texify.backend.entity.Template;
import com.texify.backend.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds built-in (system) templates at application startup.
 * <p>
 * Idempotent: it only inserts the default set when no system template exists
 * yet. With {@code ddl-auto: create-drop} (dev) the table is empty on every
 * boot, so seeding runs each time; with {@code validate}/{@code update} (prod)
 * the guard prevents duplicates.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TemplateSeeder implements CommandLineRunner {

    private final TemplateRepository templateRepository;

    @Override
    public void run(String... args) {
        if (templateRepository.countByIsSystemTrue() > 0) {
            log.debug("System templates already present — skipping seeding");
            return;
        }

        List<Template> templates = List.of(
                Template.builder()
                        .title("Academic Paper")
                        .description("A clean two-column article layout for research papers.")
                        .category("Academic")
                        .icon("📄")
                        .color("#4F46E5")
                        .previewPdfPath("/templates/previews/academic-paper.pdf")
                        .isSystem(true)
                        .build(),
                Template.builder()
                        .title("Resume / CV")
                        .description("A modern single-page curriculum vitae.")
                        .category("Career")
                        .icon("🧑‍💼")
                        .color("#059669")
                        .previewPdfPath("/templates/previews/resume.pdf")
                        .isSystem(true)
                        .build(),
                Template.builder()
                        .title("Lab Report")
                        .description("Structured report with sections for method, results and discussion.")
                        .category("Science")
                        .icon("🧪")
                        .color("#DC2626")
                        .previewPdfPath("/templates/previews/lab-report.pdf")
                        .isSystem(true)
                        .build(),
                Template.builder()
                        .title("Beamer Presentation")
                        .description("Slide deck template for talks and lectures.")
                        .category("Presentation")
                        .icon("📊")
                        .color("#D97706")
                        .previewPdfPath("/templates/previews/beamer.pdf")
                        .isSystem(true)
                        .build()
        );

        templateRepository.saveAll(templates);
        log.info("Seeded {} system template(s)", templates.size());
    }
}
