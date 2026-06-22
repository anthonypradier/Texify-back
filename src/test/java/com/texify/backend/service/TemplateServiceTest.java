package com.texify.backend.service;

import com.texify.backend.dto.CreateDocumentRequest;
import com.texify.backend.dto.DocumentResponse;
import com.texify.backend.dto.TemplateResponse;
import com.texify.backend.entity.Template;
import com.texify.backend.entity.User;
import com.texify.backend.exception.TemplateNotFoundException;
import com.texify.backend.repository.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private DocumentService documentService;

    @InjectMocks
    private TemplateService templateService;

    private static final String USER_EMAIL = "alice@example.com";

    private Template systemTemplate;

    @BeforeEach
    void setUp() {
        systemTemplate = Template.builder()
                .id(1L)
                .title("Academic Paper")
                .description("A clean article layout.")
                .blocks("[]")
                .previewPdfPath("/templates/previews/academic-paper.pdf")
                .icon("📄")
                .color("#4F46E5")
                .category("Academic")
                .isSystem(true)
                .build();
    }

    @Test
    @DisplayName("findAll: maps accessible templates to responses")
    void findAll_returnsMappedTemplates() {
        when(templateRepository.findAccessibleBy(USER_EMAIL)).thenReturn(List.of(systemTemplate));

        List<TemplateResponse> result = templateService.findAll(USER_EMAIL);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Academic Paper");
        assertThat(result.getFirst().isSystem()).isTrue();
    }

    @Test
    @DisplayName("findById: returns a system template to any user")
    void findById_systemTemplate_returnsResponse() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(systemTemplate));

        TemplateResponse result = templateService.findById(1L, USER_EMAIL);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.previewPdfPath()).isEqualTo("/templates/previews/academic-paper.pdf");
    }

    @Test
    @DisplayName("findById: unknown id throws TemplateNotFoundException")
    void findById_unknown_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.findById(99L, USER_EMAIL))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    @DisplayName("findById: another user's non-system template is hidden (404)")
    void findById_foreignUserTemplate_throws() {
        User other = User.builder().id(2L).email("bob@example.com").build();
        Template foreign = Template.builder()
                .id(5L).title("Bob's template").isSystem(false).createdBy(other).build();
        when(templateRepository.findById(5L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> templateService.findById(5L, USER_EMAIL))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    @DisplayName("findById: user's own template is returned")
    void findById_ownTemplate_returnsResponse() {
        User owner = User.builder().id(3L).email(USER_EMAIL).build();
        Template own = Template.builder()
                .id(7L).title("My template").isSystem(false).createdBy(owner).build();
        when(templateRepository.findById(7L)).thenReturn(Optional.of(own));

        TemplateResponse result = templateService.findById(7L, USER_EMAIL);

        assertThat(result.id()).isEqualTo(7L);
    }

    @Test
    @DisplayName("useTemplate: creates a document titled '<template> - copy'")
    void useTemplate_createsDocumentWithCopySuffix() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(systemTemplate));
        DocumentResponse created = sampleDocument("Academic Paper - copy");
        when(documentService.create(eq(USER_EMAIL), any(CreateDocumentRequest.class))).thenReturn(created);

        DocumentResponse result = templateService.useTemplate(1L, USER_EMAIL);

        ArgumentCaptor<CreateDocumentRequest> captor = ArgumentCaptor.forClass(CreateDocumentRequest.class);
        verify(documentService).create(eq(USER_EMAIL), captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Academic Paper - copy");
        assertThat(result.title()).isEqualTo("Academic Paper - copy");
    }

    private DocumentResponse sampleDocument(String title) {
        return new DocumentResponse(
                10L, title, "[]", false, null, 0,
                null, null, false, null, null,
                0, 0, 0, 0, 0, List.of());
    }
}
