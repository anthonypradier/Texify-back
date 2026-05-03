package com.texify.backend.controller;

import com.texify.backend.dto.CreateDocumentRequest;
import com.texify.backend.dto.DocumentResponse;
import com.texify.backend.dto.UpdateDocumentRequest;
import com.texify.backend.service.DocumentService;
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
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<DocumentResponse> create(
            @Valid @RequestBody CreateDocumentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("POST /api/documents — user '{}'", userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.create(userDetails.getUsername(), request));
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> findAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("GET /api/documents — user '{}'", userDetails.getUsername());
        return ResponseEntity.ok(documentService.findAll(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> findById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("GET /api/documents/{} — user '{}'", id, userDetails.getUsername());
        return ResponseEntity.ok(documentService.findById(id, userDetails.getUsername()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<DocumentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("PATCH /api/documents/{} — user '{}'", id, userDetails.getUsername());
        return ResponseEntity.ok(documentService.update(id, userDetails.getUsername(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("DELETE /api/documents/{} — user '{}'", id, userDetails.getUsername());
        documentService.softDelete(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bin")
    public ResponseEntity<List<DocumentResponse>> getBin(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("GET /api/documents/bin — user '{}'", userDetails.getUsername());
        return ResponseEntity.ok(documentService.getBinDocuments(userDetails.getUsername()));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<DocumentResponse> restore(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("POST /api/documents/{}/restore — user '{}'", id, userDetails.getUsername());
        return ResponseEntity.ok(documentService.restoreDocument(id, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanentDelete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("DELETE /api/documents/{}/permanent — user '{}'", id, userDetails.getUsername());
        documentService.permanentlyDeleteDocument(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/labels/{labelId}")
    public ResponseEntity<DocumentResponse> addLabel(
            @PathVariable Long id,
            @PathVariable Long labelId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("POST /api/documents/{}/labels/{} — user '{}'", id, labelId, userDetails.getUsername());
        return ResponseEntity.ok(documentService.addLabel(id, labelId, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}/labels/{labelId}")
    public ResponseEntity<DocumentResponse> removeLabel(
            @PathVariable Long id,
            @PathVariable Long labelId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("DELETE /api/documents/{}/labels/{} — user '{}'", id, labelId, userDetails.getUsername());
        return ResponseEntity.ok(documentService.removeLabel(id, labelId, userDetails.getUsername()));
    }
}
