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

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final EmailService emailService;

    /**
     * Creates a new disabled account and sends a verification email.
     *
     * @param request the registration payload
     * @return a {@link MessageResponse} asking the user to check their inbox
     * @throws EmailAlreadyExistsException if the email is already in use
     */
    @Transactional
    public MessageResponse register(RegisterRequest request) {
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
                .enabled(false)
                .build();

        userRepository.save(user);
        log.info("Account created for '{}' — email verification pending", user.getEmail());

        issueVerificationToken(user);
        return new MessageResponse(
                "Verification email sent to " + user.getEmail() + ". Please check your inbox.");
    }

    /**
     * Activates an account using the token received by email.
     *
     * @param rawToken the UUID token from the verification link
     * @return a {@link MessageResponse} confirming activation
     * @throws InvalidVerificationTokenException if the token is unknown, already used, or expired
     */
    @Transactional
    public MessageResponse verifyEmail(String rawToken) {
        VerificationToken vt = verificationTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new InvalidVerificationTokenException(
                        "Invalid or expired verification token."));

        if (vt.getUsedAt() != null) {
            throw new InvalidVerificationTokenException(
                    "This verification link has already been used.");
        }

        if (vt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidVerificationTokenException(
                    "Verification token has expired. Please request a new one.");
        }

        vt.setUsedAt(LocalDateTime.now());
        verificationTokenRepository.save(vt);

        User user = vt.getUser();
        user.setEnabled(true);
        userRepository.save(user);
        log.info("Email verified for '{}'", user.getEmail());

        return new MessageResponse("Email verified successfully. You can now log in.");
    }

    /**
     * Re-sends a verification email.
     * Always returns 200 regardless of whether the email exists to avoid leaking account info.
     *
     * @param email the address to send a new token to
     * @return a generic {@link MessageResponse}
     */
    @Transactional
    public MessageResponse resendVerification(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isEnabled()) {
                verificationTokenRepository.deleteByUserAndType(user, TokenType.EMAIL_VERIFICATION);
                issueVerificationToken(user);
                log.info("Verification email resent to '{}'", email);
            }
        });
        return new MessageResponse(
                "If an account exists for " + email + ", a new verification email has been sent.");
    }

    /**
     * Authenticates an existing verified user and issues a fresh JWT.
     * Throws {@link org.springframework.security.authentication.DisabledException}
     * (mapped to 403) if the account is not yet verified.
     *
     * @param request the login payload
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
     *
     * @param email the email extracted from the security context principal
     * @return a {@link UserResponse} with the user's public profile fields
     */
    public UserResponse getCurrentUser(String email) {
        log.debug("Fetching profile for '{}'", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for email: " + email));
        return toUserResponse(user);
    }

    /** Creates a VerificationToken, persists it, and sends the email. */
    private void issueVerificationToken(User user) {
        VerificationToken vt = VerificationToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .type(TokenType.EMAIL_VERIFICATION)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        verificationTokenRepository.save(vt);
        emailService.sendVerificationEmail(user.getEmail(), vt.getToken());
    }

    private AuthResponse toAuthResponse(String token, User user) {
        return new AuthResponse(
                token,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name()
        );
    }

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
