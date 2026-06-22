package com.texify.backend.controller;

import com.texify.backend.dto.DocumentResponse;
import com.texify.backend.dto.TemplateResponse;
import com.texify.backend.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@Slf4j
public class TemplateController {

    private final TemplateService templateService;

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
}
