package com.texify.backend.controller;

import com.texify.backend.dto.AuthResponse;
import com.texify.backend.dto.LoginRequest;
import com.texify.backend.dto.MessageResponse;
import com.texify.backend.dto.RegisterRequest;
import com.texify.backend.dto.ResendVerificationRequest;
import com.texify.backend.dto.UserResponse;
import com.texify.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Creates a new user account and sends a verification email.
     * The account is inactive until the user verifies their address.
     *
     * @param request validated registration payload
     * @return 201 Created with a {@link MessageResponse}
     */
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.debug("POST /api/auth/register — email '{}'", request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * Activates an account using the token received by email.
     *
     * @param token the UUID verification token (query param)
     * @return 200 OK with a {@link MessageResponse}
     */
    @GetMapping("/verify")
    public ResponseEntity<MessageResponse> verify(@RequestParam String token) {
        log.debug("GET /api/auth/verify");
        return ResponseEntity.ok(authService.verifyEmail(token));
    }

    /**
     * Re-sends a verification email to the given address.
     * Always returns 200 to avoid leaking whether the email exists.
     *
     * @param request payload containing the email address
     * @return 200 OK with a generic {@link MessageResponse}
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        log.debug("POST /api/auth/resend-verification — email '{}'", request.getEmail());
        return ResponseEntity.ok(authService.resendVerification(request.getEmail()));
    }

    /**
     * Authenticates an existing user and returns a fresh JWT.
     *
     * @param request validated login payload
     * @return 200 OK with an {@link AuthResponse}
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.debug("POST /api/auth/login — email '{}'", request.getEmail());
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Revokes the current JWT server-side and signals the client to discard it.
     *
     * @param authorizationHeader the full {@code Authorization: Bearer <token>} header value
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authorizationHeader) {
        log.debug("POST /api/auth/logout");
        authService.logout(authorizationHeader);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the profile of the currently authenticated user.
     *
     * @param userDetails the principal injected by Spring Security
     * @return 200 OK with a {@link UserResponse}
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        log.debug("GET /api/auth/me — '{}'", userDetails.getUsername());
        return ResponseEntity.ok(authService.getCurrentUser(userDetails.getUsername()));
    }
}
