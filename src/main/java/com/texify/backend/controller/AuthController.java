package com.texify.backend.controller;

import com.texify.backend.dto.AuthResponse;
import com.texify.backend.dto.LoginRequest;
import com.texify.backend.dto.RegisterRequest;
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

/**
 * REST controller exposing authentication endpoints.
 * <p>
 * All routes are prefixed with {@code /api/auth}.
 * {@code /register} and {@code /login} are public. All other routes require
 * a valid {@code Authorization: Bearer <token>} header.
 * </p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Creates a new user account and returns a JWT ready for immediate use.
     *
     * @param request validated registration payload
     * @return 201 Created with an {@link AuthResponse} body
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.debug("POST /api/auth/register — email '{}'", request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * Authenticates an existing user and returns a fresh JWT.
     *
     * @param request validated login payload
     * @return 200 OK with an {@link AuthResponse} body
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.debug("POST /api/auth/login — email '{}'", request.getEmail());
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Revokes the current JWT server-side and signals the client to discard it.
     * <p>
     * Requires a valid JWT in the {@code Authorization} header.
     * After a successful {@code 204} response, the client must delete its
     * local copy of the token.
     * </p>
     *
     * @param authorizationHeader the full {@code Authorization: Bearer <token>} header value
     * @return 204 No Content on success
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
     * <p>
     * Spring Security injects the {@link UserDetails} principal from the
     * security context via {@code @AuthenticationPrincipal}. The email
     * (username) is used to load the full profile from the database.
     * </p>
     *
     * @param userDetails the principal injected by Spring Security
     * @return 200 OK with a {@link UserResponse} body (no password field)
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        log.debug("GET /api/auth/me — '{}'", userDetails.getUsername());
        return ResponseEntity.ok(authService.getCurrentUser(userDetails.getUsername()));
    }
}
