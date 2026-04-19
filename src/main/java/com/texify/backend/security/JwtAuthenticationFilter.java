package com.texify.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that validates the JWT on every incoming request.
 * <p>
 * Runs exactly once per request (extends {@link OncePerRequestFilter}).
 * If a valid {@code Authorization: Bearer <token>} header is present, the
 * principal is populated in the {@link SecurityContextHolder} so that
 * downstream controllers receive a fully authenticated {@code Authentication}.
 * </p>
 * <p>
 * Requests without a token — or with an invalid one — are left unauthenticated.
 * Spring Security's access rules then decide whether to return 401.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    /**
     * Extracts the JWT from the {@code Authorization} header, validates it,
     * and populates the security context when the token is legitimate.
     *
     * @param request     the incoming HTTP request
     * @param response    the outgoing HTTP response
     * @param filterChain the remaining filter chain
     * @throws ServletException if a filter error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            final String email = jwtService.extractUsername(jwt);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authenticated '{}' via JWT for {} {}",
                            email, request.getMethod(), request.getRequestURI());
                } else {
                    log.debug("Invalid or revoked JWT for '{}' on {} {}",
                            email, request.getMethod(), request.getRequestURI());
                }
            }
        } catch (Exception e) {
            log.warn("JWT processing error on {} {}: {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
