package com.texify.backend.controller;

import tools.jackson.databind.ObjectMapper;
import com.texify.backend.dto.AuthResponse;
import com.texify.backend.dto.LoginRequest;
import com.texify.backend.dto.RegisterRequest;
import com.texify.backend.dto.UserResponse;
import com.texify.backend.exception.EmailAlreadyExistsException;
import com.texify.backend.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link AuthController}.
 * <p>
 * Uses the full Spring context with {@link AutoConfigureMockMvc} and H2 (test profile).
 * {@link AuthService} is mocked so tests are deterministic and database-independent.
 * {@link JavaMailSender} is also mocked to prevent any SMTP autoconfiguration errors.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    /** Prevents Spring Boot from trying to connect to a real mail server. */
    @MockitoBean
    private JavaMailSender javaMailSender;

    private static final AuthResponse SAMPLE_AUTH =
            new AuthResponse("jwt-token", "alice@example.com", "Alice", "Smith", "ROLE_USER");

    private static final UserResponse SAMPLE_USER =
            new UserResponse(1L, "alice@example.com", "Alice", "Smith", "ROLE_USER");

    // ── POST /api/auth/register ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /register: 201 with AuthResponse on success")
    void register_validPayload_returns201() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(SAMPLE_AUTH);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRegister(
                                "alice@example.com", "secret123", "Alice", "Smith"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("POST /register: 409 when email already exists")
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("alice@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRegister(
                                "alice@example.com", "secret123", "Alice", "Smith"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /register: 400 when email format is invalid")
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRegister(
                                "not-an-email", "secret123", "Alice", "Smith"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /register: 400 when password is shorter than 8 characters")
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRegister(
                                "alice@example.com", "abc", "Alice", "Smith"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register: 400 when first name is blank")
    void register_blankFirstName_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRegister(
                                "alice@example.com", "secret123", "", "Smith"))))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/login ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /login: 200 with AuthResponse on valid credentials")
    void login_validCredentials_returns200() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(SAMPLE_AUTH);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLogin(
                                "alice@example.com", "secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("POST /login: 401 on wrong credentials")
    void login_wrongCredentials_returns401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLogin(
                                "alice@example.com", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /login: 400 when body is missing required fields")
    void login_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/logout ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /logout: 204 when user is authenticated")
    @WithMockUser(username = "alice@example.com")
    void logout_authenticated_returns204() throws Exception {
        doNothing().when(authService).logout(anyString());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer jwt-token"))
                .andExpect(status().isNoContent());

        verify(authService).logout("Bearer jwt-token");
    }

    @Test
    @DisplayName("POST /logout: 401 when no authentication is present")
    void logout_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/auth/me ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /me: 200 with UserResponse for authenticated user")
    @WithMockUser(username = "alice@example.com")
    void me_authenticated_returns200() throws Exception {
        when(authService.getCurrentUser("alice@example.com")).thenReturn(SAMPLE_USER);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("GET /me: 401 when no authentication is present")
    void me_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private RegisterRequest buildRegister(String email, String password,
                                           String firstName, String lastName) {
        RegisterRequest r = new RegisterRequest();
        r.setEmail(email);
        r.setPassword(password);
        r.setFirstName(firstName);
        r.setLastName(lastName);
        return r;
    }

    private LoginRequest buildLogin(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }
}
