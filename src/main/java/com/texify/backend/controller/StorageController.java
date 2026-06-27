package com.texify.backend.controller;

import com.texify.backend.service.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves files from the local storage over HTTP (dev only).
 * <p>
 * In production this is replaced by Nginx or a CDN. Template previews are
 * public (see {@code SecurityConfig}); document files would need auth and are
 * not exposed through a blanket public mapping yet.
 * </p>
 */
@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    /** Serves a stored file by its relative path. */
    @GetMapping("/**")
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) {
        String relativePath = request.getRequestURI()
                .substring(request.getContextPath().length() + "/storage/".length());

        if (!storageService.exists(relativePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = storageService.load(relativePath);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(determineContentType(relativePath)))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400") // 24h
                .body(resource);
    }

    private String determineContentType(String path) {
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".pdf")) return "application/pdf";
        if (path.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
