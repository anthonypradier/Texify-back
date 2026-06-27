package com.texify.backend.service;

import com.texify.backend.dto.CreateDocumentRequest;
import com.texify.backend.dto.DocumentResponse;
import com.texify.backend.dto.TemplateResponse;
import com.texify.backend.entity.Template;
import com.texify.backend.exception.TemplateNotFoundException;
import com.texify.backend.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final DocumentService documentService;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public List<TemplateResponse> findAll(String userEmail) {
        log.debug("Listing templates for user '{}'", userEmail);
        return templateRepository.findAccessibleBy(userEmail)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse findById(Long id, String userEmail) {
        log.debug("Fetching template {} for user '{}'", id, userEmail);
        return toResponse(getAccessible(id, userEmail));
    }

    /**
     * Creates a brand-new document for the user from the given template.
     * <p>
     * No template data is copied into the document beyond the title (suffixed
     * with {@code " - copy"}); the document starts with empty blocks.
     * </p>
     */
    @Transactional
    public DocumentResponse useTemplate(Long id, String userEmail) {
        log.info("Creating document from template {} for user '{}'", id, userEmail);
        Template template = getAccessible(id, userEmail);
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setTitle(template.getTitle() + " - copy");
        return documentService.create(userEmail, request);
    }

    private Template getAccessible(Long id, String userEmail) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new TemplateNotFoundException(id));
        boolean owned = template.getCreatedBy() != null
                && userEmail.equals(template.getCreatedBy().getEmail());
        if (!template.isSystem() && !owned) {
            throw new TemplateNotFoundException(id);
        }
        return template;
    }

    /**
     * Maps a template to its API view, building public URLs for the preview
     * files. Public so {@link TemplatePreviewService} can reuse it after an
     * upload without duplicating the mapping.
     */
    public TemplateResponse toResponse(Template t) {
        return new TemplateResponse(
                t.getId(),
                t.getTitle(),
                t.getDescription(),
                t.getBlocks(),
                t.getPreviewPdfPath(),
                storageService.buildPublicUrl(t.getPreviewImagePath()),
                storageService.buildPublicUrl(t.getPreviewPdfPath()),
                t.getPreviewStatus(),
                t.getIcon(),
                t.getColor(),
                t.getCategory(),
                t.isSystem(),
                t.getCreatedAt()
        );
    }
}
