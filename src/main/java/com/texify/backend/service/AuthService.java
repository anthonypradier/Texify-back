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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for user authentication: registration, login, logout,
 * and current-user retrieval.
 * <p>
 * Credential verification is delegated to Spring Security's
 * {@link AuthenticationManager}; token lifecycle is managed by {@link JwtService}.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    /**
     * Creates a new user account and returns a JWT for immediate use.
     * <p>
     * The plain-text password is BCrypt-hashed before persistence. Every new
     * account is assigned {@link Role#ROLE_USER} and starts enabled.
     * </p>
     *
     * @param request the registration payload (email, password, first/last name)
     * @return an {@link AuthResponse} containing the JWT and basic user info
     * @throws EmailAlreadyExistsException if the email is already in use
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registration attempt for '{}'", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.ROLE_USER)
                .enabled(true)
                .build();

        userRepository.save(user);
        log.info("Account created for '{}' (id={})", user.getEmail(), user.getId());

        UserDetails principal = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(principal);
        return toAuthResponse(token, user);
    }

    /**
     * Authenticates an existing user and issues a fresh JWT.
     * <p>
     * Delegates credential verification to the {@link AuthenticationManager},
     * which runs the BCrypt comparison internally via
     * {@link UserDetailsServiceImpl}. A
     * {@link org.springframework.security.authentication.BadCredentialsException}
     * is thrown on failure and mapped to 401 by the global exception handler.
     * </p>
     *
     * @param request the login payload (email + plain-text password)
     * @return an {@link AuthResponse} containing the JWT and basic user info
     */
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for '{}'", request.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException(
                        "User disappeared after successful authentication: " + request.getEmail()));

        UserDetails principal = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(principal);
        log.info("Login successful for '{}' (id={})", user.getEmail(), user.getId());
        return toAuthResponse(token, user);
    }

    /**
     * Revokes the JWT extracted from the {@code Authorization} header.
     * <p>
     * The token is added to an in-memory blacklist; the client must also
     * discard its local copy.
     * </p>
     *
     * @param authorizationHeader the full {@code Authorization: Bearer <token>} header value
     */
    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authorizationHeader.substring(7);
        String email = jwtService.extractUsername(token);
        jwtService.revoke(token);
        log.info("User '{}' logged out — token revoked", email);
    }

    /**
     * Returns the profile of the currently authenticated user.
     * <p>
     * Requires a fresh DB lookup because the security context only holds the
     * email (Spring Security's "username"), not the full entity.
     * </p>
     *
     * @param email the email extracted from the security context principal
     * @return a {@link UserResponse} with the user's public profile fields
     * @throws UsernameNotFoundException if no account exists for the given email
     */
    public UserResponse getCurrentUser(String email) {
        log.debug("Fetching profile for '{}'", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for email: " + email));
        return toUserResponse(user);
    }

    /**
     * Maps a JWT and a {@link User} entity to an {@link AuthResponse}.
     *
     * @param token the signed JWT
     * @param user  the authenticated or newly created user
     * @return the assembled response DTO
     */
    private AuthResponse toAuthResponse(String token, User user) {
        return new AuthResponse(
                token,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name()
        );
    }

    /**
     * Maps a {@link User} entity to a {@link UserResponse}.
     *
     * @param user the entity to convert
     * @return the public-facing user DTO
     */
    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name()
        );
    }
}
