package com.texify.backend.controller;

import com.texify.backend.dto.DocumentResponse;
import com.texify.backend.dto.TemplateResponse;
import com.texify.backend.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TemplateService templateService;

    /** Prevents Spring Boot from trying to connect to a real mail server. */
    @MockitoBean
    private JavaMailSender javaMailSender;

    private static final TemplateResponse SAMPLE_TEMPLATE = new TemplateResponse(
            1L, "Academic Paper", "A clean article layout.", "[]",
            "/templates/previews/academic-paper.pdf", "📄", "#4F46E5", "Academic",
            true, null);

    private static final DocumentResponse SAMPLE_DOCUMENT = new DocumentResponse(
            10L, "Academic Paper - copy", "[]", false, null, 0,
            null, null, false, null, null,
            0, 0, 0, 0, 0, List.of());

    @Test
    @DisplayName("GET /api/templates: 200 with template list")
    @WithMockUser(username = "alice@example.com")
    void findAll_returns200() throws Exception {
        when(templateService.findAll(anyString())).thenReturn(List.of(SAMPLE_TEMPLATE));

        mockMvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Academic Paper"))
                .andExpect(jsonPath("$[0].isSystem").value(true));
    }

    @Test
    @DisplayName("GET /api/templates/{id}: 200 with template")
    @WithMockUser(username = "alice@example.com")
    void findById_returns200() throws Exception {
        when(templateService.findById(eq(1L), anyString())).thenReturn(SAMPLE_TEMPLATE);

        mockMvc.perform(get("/api/templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewPdfPath").value("/templates/previews/academic-paper.pdf"));
    }

    @Test
    @DisplayName("POST /api/templates/{id}/use: 201 with created document")
    @WithMockUser(username = "alice@example.com")
    void use_returns201() throws Exception {
        when(templateService.useTemplate(eq(1L), anyString())).thenReturn(SAMPLE_DOCUMENT);

        mockMvc.perform(post("/api/templates/1/use"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Academic Paper - copy"));
    }

    @Test
    @DisplayName("GET /api/templates: 401 without authentication")
    void findAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/templates"))
                .andExpect(status().isUnauthorized());
    }
}
