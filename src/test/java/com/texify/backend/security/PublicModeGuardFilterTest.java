package com.texify.backend.security;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the real (non-cosmetic) lock enforced by {@link PublicModeGuardFilter}
 * when {@code app.public-mode=true}: MVP routes are blocked server-side (404)
 * while only the waitlist and health endpoints stay reachable.
 */
@SpringBootTest(properties = "app.public-mode=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicModeGuardFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WaitlistService waitlistService;

    /** Prevents Spring Boot from trying to connect to a real mail server. */
    @MockitoBean
    private JavaMailSender javaMailSender;

    @Test
    @DisplayName("public mode: an MVP route is blocked with 404 (not 401)")
    void publicMode_blocksMvpRoute_returns404() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("public mode: a public auth route is also blocked with 404")
    void publicMode_blocksAuthRoute_returns404() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("public mode: waitlist endpoint stays reachable (200)")
    void publicMode_allowsWaitlist_returns200() throws Exception {
        mockMvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bob@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("public mode: health endpoint stays reachable (200)")
    void publicMode_allowsHealth_returns200() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}
