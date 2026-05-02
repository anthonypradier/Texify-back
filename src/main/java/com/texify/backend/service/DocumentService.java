package com.texify.backend.service;

import com.texify.backend.dto.CreateDocumentRequest;
import com.texify.backend.dto.DocumentResponse;
import com.texify.backend.dto.LabelResponse;
import com.texify.backend.dto.UpdateDocumentRequest;
import com.texify.backend.entity.Document;
import com.texify.backend.entity.Label;
import com.texify.backend.entity.User;
import com.texify.backend.exception.DocumentNotFoundException;
import com.texify.backend.exception.LabelNotFoundException;
import com.texify.backend.repository.DocumentRepository;
import com.texify.backend.repository.LabelRepository;
import com.texify.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final LabelRepository labelRepository;
    private final UserRepository userRepository;

    @Transactional
    public DocumentResponse create(String ownerEmail, CreateDocumentRequest request) {
        log.info("Creating document for user '{}'", ownerEmail);
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + ownerEmail));
        Document document = Document.builder()
                .title(request.getTitle())
                .owner(owner)
                .icon(request.getIcon())
                .color(request.getColor())
                .isPublic(Boolean.TRUE.equals(request.getIsPublic()))
                .build();
        return toResponse(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findAll(String ownerEmail) {
        log.debug("Listing documents for user '{}'", ownerEmail);
        return documentRepository.findByOwnerEmailAndDeletedAtIsNull(ownerEmail)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse findById(Long id, String ownerEmail) {
        log.debug("Fetching document {} for user '{}'", id, ownerEmail);
        return toResponse(documentRepository.findByIdAndOwnerEmailAndDeletedAtIsNull(id, ownerEmail)
                .orElseThrow(() -> new DocumentNotFoundException(id)));
    }

    @Transactional
    public DocumentResponse update(Long id, String ownerEmail, UpdateDocumentRequest request) {
        log.info("Updating document {} for user '{}'", id, ownerEmail);
        Document document = documentRepository.findByIdAndOwnerEmailAndDeletedAtIsNull(id, ownerEmail)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        if (request.getTitle() != null) document.setTitle(request.getTitle());
        if (request.getBlocks() != null) document.setBlocks(request.getBlocks());
        if (request.getIsPublic() != null) document.setPublic(request.getIsPublic());
        if (request.getPinned() != null) document.setPinned(request.getPinned());
        if (request.getIcon() != null) document.setIcon(request.getIcon());
        if (request.getColor() != null) document.setColor(request.getColor());
        if (request.getWordCount() != null) document.setWordCount(request.getWordCount());
        if (request.getEquationCount() != null) document.setEquationCount(request.getEquationCount());
        if (request.getFigureCount() != null) document.setFigureCount(request.getFigureCount());
        if (request.getPlotCount() != null) document.setPlotCount(request.getPlotCount());
        if (request.getCodeCount() != null) document.setCodeCount(request.getCodeCount());
        if (request.getCompilationCount() != null) document.setCompilationCount(request.getCompilationCount());
        return toResponse(documentRepository.save(document));
    }

    @Transactional
    public void softDelete(Long id, String ownerEmail) {
        log.info("Soft-deleting document {} for user '{}'", id, ownerEmail);
        Document document = documentRepository.findByIdAndOwnerEmailAndDeletedAtIsNull(id, ownerEmail)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    @Transactional
    public DocumentResponse addLabel(Long documentId, Long labelId, String ownerEmail) {
        log.info("Adding label {} to document {} for user '{}'", labelId, documentId, ownerEmail);
        Document document = documentRepository.findByIdAndOwnerEmailAndDeletedAtIsNull(documentId, ownerEmail)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        Label label = labelRepository.findByIdAndUserEmail(labelId, ownerEmail)
                .orElseThrow(() -> new LabelNotFoundException(labelId));
        if (!label.getDocuments().contains(document)) {
            label.getDocuments().add(document);
            document.getLabels().add(label);
            labelRepository.save(label);
        }
        return toResponse(document);
    }

    @Transactional
    public DocumentResponse removeLabel(Long documentId, Long labelId, String ownerEmail) {
        log.info("Removing label {} from document {} for user '{}'", labelId, documentId, ownerEmail);
        Document document = documentRepository.findByIdAndOwnerEmailAndDeletedAtIsNull(documentId, ownerEmail)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        Label label = labelRepository.findByIdAndUserEmail(labelId, ownerEmail)
                .orElseThrow(() -> new LabelNotFoundException(labelId));
        label.getDocuments().remove(document);
        document.getLabels().remove(label);
        labelRepository.save(label);
        return toResponse(document);
    }

    private DocumentResponse toResponse(Document doc) {
        List<LabelResponse> labelResponses = doc.getLabels().stream()
                .map(l -> new LabelResponse(l.getId(), l.getName(), l.getColor()))
                .toList();
        return new DocumentResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getBlocks(),
                doc.isPublic(),
                doc.getCompilePdfPath(),
                doc.getWordCount(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                doc.isPinned(),
                doc.getIcon(),
                doc.getColor(),
                doc.getEquationCount(),
                doc.getFigureCount(),
                doc.getPlotCount(),
                doc.getCodeCount(),
                doc.getCompilationCount(),
                labelResponses
        );
    }
}
