package com.texify.backend.controller;

import com.texify.backend.dto.CreateLabelRequest;
import com.texify.backend.dto.LabelResponse;
import com.texify.backend.dto.UpdateLabelRequest;
import com.texify.backend.service.LabelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/labels")
@RequiredArgsConstructor
@Slf4j
public class LabelController {

    private final LabelService labelService;

    @PostMapping
    public ResponseEntity<LabelResponse> create(
            @Valid @RequestBody CreateLabelRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("POST /api/labels — user '{}'", userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(labelService.create(userDetails.getUsername(), request));
    }

    @GetMapping
    public ResponseEntity<List<LabelResponse>> findAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("GET /api/labels — user '{}'", userDetails.getUsername());
        return ResponseEntity.ok(labelService.findAll(userDetails.getUsername()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<LabelResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLabelRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("PATCH /api/labels/{} — user '{}'", id, userDetails.getUsername());
        return ResponseEntity.ok(labelService.update(id, userDetails.getUsername(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("DELETE /api/labels/{} — user '{}'", id, userDetails.getUsername());
        labelService.delete(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
