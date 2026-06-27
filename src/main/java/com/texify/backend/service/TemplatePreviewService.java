package com.texify.backend.service;

import com.texify.backend.dto.TemplateResponse;
import com.texify.backend.entity.PreviewStatus;
import com.texify.backend.entity.Template;
import com.texify.backend.exception.InvalidFileException;
import com.texify.backend.exception.StorageException;
import com.texify.backend.exception.TemplateNotFoundException;
import com.texify.backend.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Owns the template preview lifecycle: manual upload (used for system templates
 * until the LaTeX compiler exists), staleness detection, invalidation, cleanup,
 * and an async generation stub to be wired to the compiler later.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TemplatePreviewService {

    private final TemplateRepository templateRepository;
    private final StorageService storageService;
    private final TemplateService templateService;

    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;   // 5 MB
    private static final long MAX_PDF_BYTES = 20 * 1024 * 1024;    // 20 MB

    // ── Staleness check ──────────────────────────────────────────────────────

    /**
     * A preview is up to date only if it was generated and the blocks have not
     * changed since, and its status is not PENDING / OUTDATED / ERROR.
     */
    public boolean isPreviewUpToDate(Template template) {
        if (template.getPreviewGeneratedAt() == null) {
            return false;
        }
        PreviewStatus status = template.getPreviewStatus();
        if (status == PreviewStatus.PENDING
                || status == PreviewStatus.OUTDATED
                || status == PreviewStatus.ERROR) {
            return false;
        }
        return !template.getBlocksUpdatedAt().isAfter(template.getPreviewGeneratedAt());
    }

    // ── Manual upload (system templates, before the compiler exists) ─────────

    /** Uploads a PNG/JPG/WebP preview image for a template (admin-only). */
    @Transactional
    public TemplateResponse uploadPreviewImage(Long templateId, MultipartFile imageFile) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException(templateId));

        validateImageFile(imageFile);

        if (template.getPreviewImagePath() != null) {
            storageService.delete(template.getPreviewImagePath());
        }

        String relativePath = buildPreviewImagePath(templateId);
        try {
            storageService.store(imageFile.getInputStream(), relativePath);
        } catch (IOException e) {
            throw new StorageException("Failed to store preview image: " + e.getMessage(), e);
        }

        template.setPreviewImagePath(relativePath);
        template.setPreviewGeneratedAt(LocalDateTime.now());
        template.setPreviewStatus(PreviewStatus.READY);
        templateRepository.save(template);

        log.info("Preview image uploaded manually for template {}", templateId);
        return templateService.toResponse(template);
    }

    /** Uploads the full PDF for a template (admin-only). */
    @Transactional
    public TemplateResponse uploadPreviewPdf(Long templateId, MultipartFile pdfFile) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException(templateId));

        validatePdfFile(pdfFile);

        if (template.getPreviewPdfPath() != null && !template.getPreviewPdfPath().startsWith("/")) {
            // Only delete previously uploaded files, never the seeded static assets.
            storageService.delete(template.getPreviewPdfPath());
        }

        String relativePath = buildPreviewPdfPath(templateId);
        try {
            storageService.store(pdfFile.getInputStream(), relativePath);
        } catch (IOException e) {
            throw new StorageException("Failed to store preview PDF: " + e.getMessage(), e);
        }

        template.setPreviewPdfPath(relativePath);
        // The image drives the gallery thumbnail; the PDF is a bonus, so the
        // status is left untouched here.
        templateRepository.save(template);

        log.info("Preview PDF uploaded manually for template {}", templateId);
        return templateService.toResponse(template);
    }

    // ── Invalidation ─────────────────────────────────────────────────────────

    /**
     * Marks a template's preview as stale. Call whenever the blocks change.
     * The async generator (once wired to the compiler) will rebuild it.
     */
    @Transactional
    public void invalidatePreview(Template template) {
        template.setPreviewStatus(PreviewStatus.OUTDATED);
        template.setBlocksUpdatedAt(LocalDateTime.now());
        templateRepository.save(template);
        log.info("Preview invalidated for template {}", template.getId());
        // TODO: trigger generatePreviewAsync(template.getId()) once the compiler exists.
    }

    // ── Async generation stub (wired to the LaTeX compiler later) ────────────

    /**
     * STUB — automatic preview generation via the LaTeX compiler.
     * <p>
     * Planned flow: blocks JSON → assemble .tex → compile (tectonic) → extract
     * page 1 as PNG (PDFBox) → store PNG + PDF → status READY. For now it just
     * flips the status to GENERATING then back to PENDING.
     * </p>
     */
    @Async
    @Transactional
    public CompletableFuture<Void> generatePreviewAsync(Long templateId) {
        log.info("STUB: generatePreviewAsync for template {} — compiler not available", templateId);

        templateRepository.findById(templateId).ifPresent(template -> {
            template.setPreviewStatus(PreviewStatus.GENERATING);
            templateRepository.save(template);
        });

        // TODO: assemble .tex → compile → extract PNG → store → mark READY.

        templateRepository.findById(templateId).ifPresent(template -> {
            template.setPreviewStatus(PreviewStatus.PENDING);
            templateRepository.save(template);
        });

        return CompletableFuture.completedFuture(null);
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    /** Deletes a template's preview files. Skips seeded static assets ({@code "/"} prefix). */
    public void deletePreviewFiles(Template template) {
        if (template.getPreviewImagePath() != null && !template.getPreviewImagePath().startsWith("/")) {
            storageService.delete(template.getPreviewImagePath());
        }
        if (template.getPreviewPdfPath() != null && !template.getPreviewPdfPath().startsWith("/")) {
            storageService.delete(template.getPreviewPdfPath());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildPreviewImagePath(Long templateId) {
        return "templates/previews/" + templateId + "/preview.png";
    }

    private String buildPreviewPdfPath(Long templateId) {
        return "templates/pdfs/" + templateId + "/template.pdf";
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("The image file is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidFileException("The file must be an image (PNG, JPG, WebP)");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new InvalidFileException("The image must not exceed 5 MB");
        }
    }

    private void validatePdfFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("The PDF file is empty");
        }
        if (!"application/pdf".equals(file.getContentType())) {
            throw new InvalidFileException("The file must be a PDF");
        }
        if (file.getSize() > MAX_PDF_BYTES) {
            throw new InvalidFileException("The PDF must not exceed 20 MB");
        }
    }
}
