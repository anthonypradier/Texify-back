package com.texify.backend.service;

import com.texify.backend.dto.AuthResponse;
import com.texify.backend.dto.LoginRequest;
import com.texify.backend.dto.RegisterRequest;
import com.texify.backend.dto.UserResponse;
import com.texify.backend.entity.Role;
import com.texify.backend.entity.User;
import com.texify.backend.exception.EmailAlreadyExistsException;
import com.texify.backend.repository.UserRepository;
import com.texify.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}.
 * <p>
 * All dependencies are mocked with Mockito so the suite runs in complete
 * isolation — no database, no Spring context.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User persistedUser;
    private UserDetails mockPrincipal;

    /** Builds shared fixtures used across most test methods. */
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

        persistedUser = User.builder()
                .id(1L)
                .email("alice@example.com")
                .password("$2a$10$hashedPassword")
                .firstName("Alice")
                .lastName("Smith")
                .role(Role.ROLE_USER)
                .enabled(true)
                .build();

        mockPrincipal = org.springframework.security.core.userdetails.User
                .withUsername("alice@example.com")
                .password("$2a$10$hashedPassword")
                .authorities("ROLE_USER")
                .build();
    }

    // ── register ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: returns AuthResponse with JWT when email is new")
    void register_newEmail_returnsAuthResponse() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(persistedUser);
        when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(mockPrincipal);
        when(jwtService.generateToken(mockPrincipal)).thenReturn("jwt-token");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.firstName()).isEqualTo("Alice");
        assertThat(response.lastName()).isEqualTo("Smith");
        assertThat(response.role()).isEqualTo("ROLE_USER");

        verify(userRepository).save(any(User.class));
        verify(passwordEncoder).encode("secret123");
    }

    @Test
    @DisplayName("register: throws EmailAlreadyExistsException when email is taken")
    void register_duplicateEmail_throwsException() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("alice@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: new accounts always receive ROLE_USER")
    void register_newUser_assignsRoleUser() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(persistedUser);
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(mockPrincipal);
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("token");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.role()).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("register: password is hashed before persistence")
    void register_passwordIsHashed() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenReturn(persistedUser);
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(mockPrincipal);
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("token");

        authService.register(registerRequest);

        verify(passwordEncoder).encode("secret123");
    }

    // ── login ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: returns AuthResponse with JWT on valid credentials")
    void login_validCredentials_returnsAuthResponse() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(persistedUser));
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
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(persistedUser));

        UserResponse response = authService.getCurrentUser("alice@example.com");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.firstName()).isEqualTo("Alice");
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
