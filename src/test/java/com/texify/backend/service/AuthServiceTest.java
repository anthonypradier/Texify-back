package com.texify.backend.service;

import com.texify.backend.dto.AuthResponse;
import com.texify.backend.dto.LoginRequest;
import com.texify.backend.dto.MessageResponse;
import com.texify.backend.dto.RegisterRequest;
import com.texify.backend.dto.UserResponse;
import com.texify.backend.entity.Role;
import com.texify.backend.entity.TokenType;
import com.texify.backend.entity.User;
import com.texify.backend.entity.VerificationToken;
import com.texify.backend.exception.EmailAlreadyExistsException;
import com.texify.backend.exception.InvalidVerificationTokenException;
import com.texify.backend.repository.UserRepository;
import com.texify.backend.repository.VerificationTokenRepository;
import com.texify.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserDetailsService userDetailsService;
    @Mock private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User verifiedUser;
    private User unverifiedUser;
    private UserDetails mockPrincipal;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("alice@example.com");
        registerRequest.setPassword("secret123");
        registerRequest.setFirstName("Alice");
        registerRequest.setLastName("Smith");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("alice@example.com");
        loginRequest.setPassword("secret123");

        verifiedUser = User.builder()
                .id(1L).email("alice@example.com")
                .password("$2a$10$hashedPassword")
                .firstName("Alice").lastName("Smith")
                .role(Role.ROLE_USER).enabled(true)
                .build();

        unverifiedUser = User.builder()
                .id(2L).email("bob@example.com")
                .password("$2a$10$hashedPassword")
                .firstName("Bob").lastName("Jones")
                .role(Role.ROLE_USER).enabled(false)
                .build();

        mockPrincipal = org.springframework.security.core.userdetails.User
                .withUsername("alice@example.com")
                .password("$2a$10$hashedPassword")
                .authorities("ROLE_USER")
                .build();
    }

    // ── register ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: returns MessageResponse and sends email when email is new")
    void register_newEmail_returnsMessageAndSendsEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(verifiedUser);

        MessageResponse response = authService.register(registerRequest);

        assertThat(response.message()).contains("alice@example.com");
        verify(userRepository).save(any(User.class));
        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendVerificationEmail(eq("alice@example.com"), anyString());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("register: saved user is disabled with ROLE_USER")
    void register_newUser_isDisabledWithRoleUser() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(registerRequest);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();

        assertThat(saved.isEnabled()).isFalse();
        assertThat(saved.getRole()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    @DisplayName("register: VerificationToken is persisted with correct type and future expiry")
    void register_createsVerificationTokenWithExpiry() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(registerRequest);

        ArgumentCaptor<VerificationToken> vtCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(vtCaptor.capture());
        VerificationToken vt = vtCaptor.getValue();

        assertThat(vt.getType()).isEqualTo(TokenType.EMAIL_VERIFICATION);
        assertThat(vt.getToken()).isNotBlank();
        assertThat(vt.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(vt.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("register: throws EmailAlreadyExistsException when email is taken")
    void register_duplicateEmail_throwsException() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("alice@example.com");

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    // ── verifyEmail ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("verifyEmail: activates user and marks token as used on valid token")
    void verifyEmail_validToken_activatesAccount() {
        VerificationToken vt = VerificationToken.builder()
                .id(1L).user(unverifiedUser).token("valid-token")
                .type(TokenType.EMAIL_VERIFICATION)
                .expiresAt(LocalDateTime.now().plusHours(12))
                .build();

        when(verificationTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(vt));
        when(verificationTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse response = authService.verifyEmail("valid-token");

        assertThat(response.message()).containsIgnoringCase("verified");
        assertThat(vt.getUsedAt()).isNotNull();
        assertThat(unverifiedUser.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("verifyEmail: throws when token is unknown")
    void verifyEmail_unknownToken_throws() {
        when(verificationTokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bad-token"))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    @DisplayName("verifyEmail: throws when token is already used")
    void verifyEmail_alreadyUsed_throws() {
        VerificationToken vt = VerificationToken.builder()
                .id(1L).user(unverifiedUser).token("used-token")
                .type(TokenType.EMAIL_VERIFICATION)
                .expiresAt(LocalDateTime.now().plusHours(12))
                .usedAt(LocalDateTime.now().minusMinutes(10))
                .build();

        when(verificationTokenRepository.findByToken("used-token")).thenReturn(Optional.of(vt));

        assertThatThrownBy(() -> authService.verifyEmail("used-token"))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    @DisplayName("verifyEmail: throws when token is expired")
    void verifyEmail_expiredToken_throws() {
        VerificationToken vt = VerificationToken.builder()
                .id(1L).user(unverifiedUser).token("expired-token")
                .type(TokenType.EMAIL_VERIFICATION)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();

        when(verificationTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(vt));

        assertThatThrownBy(() -> authService.verifyEmail("expired-token"))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessageContaining("expired");
    }

    // ── resendVerification ───────────────────────────────────────────────────

    @Test
    @DisplayName("resendVerification: deletes old tokens and sends new email for unverified user")
    void resendVerification_unverifiedUser_replacesTokenAndSendsEmail() {
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(unverifiedUser));

        authService.resendVerification("bob@example.com");

        verify(verificationTokenRepository).deleteByUserAndType(unverifiedUser, TokenType.EMAIL_VERIFICATION);
        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendVerificationEmail(eq("bob@example.com"), anyString());
    }

    @Test
    @DisplayName("resendVerification: does nothing for already-verified user")
    void resendVerification_alreadyVerified_doesNothing() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(verifiedUser));

        authService.resendVerification("alice@example.com");

        verify(verificationTokenRepository, never()).deleteByUserAndType(any(), any());
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    @DisplayName("resendVerification: returns generic message for unknown email")
    void resendVerification_unknownEmail_returnsGenericMessage() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        MessageResponse response = authService.resendVerification("unknown@example.com");

        assertThat(response.message()).isNotBlank();
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    // ── login ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: returns AuthResponse with JWT on valid credentials")
    void login_validCredentials_returnsAuthResponse() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(verifiedUser));
        when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(mockPrincipal);
        when(jwtService.generateToken(mockPrincipal)).thenReturn("jwt-token");

        AuthResponse response = authService.login(loginRequest);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.email()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("login: propagates BadCredentialsException on wrong password")
    void login_wrongPassword_throws() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(anyString());
    }

    // ── logout ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout: revokes token when valid Bearer header is provided")
    void logout_validHeader_revokesToken() {
        when(jwtService.extractUsername("valid-token")).thenReturn("alice@example.com");

        authService.logout("Bearer valid-token");

        verify(jwtService).revoke("valid-token");
    }

    @Test
    @DisplayName("logout: does nothing when Authorization header is null")
    void logout_nullHeader_doesNothing() {
        authService.logout(null);
        verify(jwtService, never()).revoke(anyString());
    }

    @Test
    @DisplayName("logout: does nothing when header has no Bearer prefix")
    void logout_noBearerPrefix_doesNothing() {
        authService.logout("Basic dXNlcjpwYXNz");
        verify(jwtService, never()).revoke(anyString());
    }

    // ── getCurrentUser ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentUser: returns UserResponse for existing email")
    void getCurrentUser_existingEmail_returnsResponse() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(verifiedUser));

        UserResponse response = authService.getCurrentUser("alice@example.com");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.role()).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("getCurrentUser: throws UsernameNotFoundException for unknown email")
    void getCurrentUser_unknownEmail_throws() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
