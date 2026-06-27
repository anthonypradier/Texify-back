package com.texify.backend.controller;

import com.texify.backend.dto.DocumentResponse;
import com.texify.backend.dto.TemplateResponse;
import com.texify.backend.service.TemplatePreviewService;
import com.texify.backend.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@Slf4j
public class TemplateController {

    private final TemplateService templateService;
    private final TemplatePreviewService templatePreviewService;

    @GetMapping
    public ResponseEntity<List<TemplateResponse>> findAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("GET /api/templates — user '{}'", userDetails.getUsername());
        return ResponseEntity.ok(templateService.findAll(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> findById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("GET /api/templates/{} — user '{}'", id, userDetails.getUsername());
        return ResponseEntity.ok(templateService.findById(id, userDetails.getUsername()));
    }

    @PostMapping("/{id}/use")
    public ResponseEntity<DocumentResponse> use(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("POST /api/templates/{}/use — user '{}'", id, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.useTemplate(id, userDetails.getUsername()));
    }

    /**
     * Uploads a preview image (PNG/JPG/WebP) for a template. Admin-only —
     * used to seed previews of system templates until the compiler exists.
     */
    @PostMapping("/{id}/preview/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TemplateResponse> uploadPreviewImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        log.debug("POST /api/templates/{}/preview/image", id);
        return ResponseEntity.ok(templatePreviewService.uploadPreviewImage(id, file));
    }

    /** Uploads the full preview PDF for a template. Admin-only. */
    @PostMapping("/{id}/preview/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TemplateResponse> uploadPreviewPdf(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        log.debug("POST /api/templates/{}/preview/pdf", id);
        return ResponseEntity.ok(templatePreviewService.uploadPreviewPdf(id, file));
    }

    /**
     * Returns the preview status of a template — polled by the frontend while
     * a generation is in progress.
     */
    @GetMapping("/{id}/preview/status")
    public ResponseEntity<TemplateResponse> getPreviewStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("GET /api/templates/{}/preview/status", id);
        return ResponseEntity.ok(templateService.findById(id, userDetails.getUsername()));
    }
}
