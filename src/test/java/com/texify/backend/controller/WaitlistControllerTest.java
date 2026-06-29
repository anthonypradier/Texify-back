package com.texify.backend.controller;

import com.texify.backend.service.WaitlistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WaitlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WaitlistService waitlistService;

    /** Prevents Spring Boot from trying to connect to a real mail server. */
    @MockitoBean
    private JavaMailSender javaMailSender;

    @Test
    @DisplayName("POST /api/waitlist: 200 and delegates to the service (no auth required)")
    void subscribe_validEmail_returns200() throws Exception {
        mockMvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(waitlistService).subscribe("alice@example.com");
    }

    @Test
    @DisplayName("POST /api/waitlist: 400 on invalid email format")
    void subscribe_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(waitlistService);
    }

    @Test
    @DisplayName("POST /api/waitlist: 400 on blank email")
    void subscribe_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(waitlistService);
    }
}
