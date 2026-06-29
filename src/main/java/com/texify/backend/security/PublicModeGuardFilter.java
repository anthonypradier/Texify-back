package com.texify.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Enforces "public (landing) mode" at the HTTP layer.
 * <p>
 * When {@code app.public-mode=true}, only the waitlist email-capture endpoint
 * and the health check are reachable; every other request is answered with a
 * {@code 404 Not Found} (chosen over 403 so the existence of the MVP API is not
 * revealed). When {@code app.public-mode=false} (the default), this filter is a
 * complete no-op — the MVP behaves exactly as before.
 * <p>
 * The lock is real, not cosmetic: requests are stopped server-side before
 * reaching any controller, so the frontend cannot bypass it.
 */
@Component
@Slf4j
public class PublicModeGuardFilter extends OncePerRequestFilter {

    /** Paths reachable while public mode is active (Ant patterns). */
    private static final List<String> PUBLIC_MODE_WHITELIST = List.of(
            "/api/waitlist",
            "/api/health",
            "/api/health/**"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${app.public-mode:false}")
    private boolean publicMode;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Normal mode → no-op: the MVP is fully functional.
        if (!publicMode) {
            filterChain.doFilter(request, response);
            return;
        }

        // Let CORS preflight through so the landing page can call the waitlist.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isWhitelisted(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("Public mode: blocked {} {}", request.getMethod(), request.getRequestURI());
        writeNotFound(response);
    }

    private boolean isWhitelisted(HttpServletRequest request) {
        String path = request.getServletPath();
        return PUBLIC_MODE_WHITELIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private void writeNotFound(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String body = """
                {"status":404,"error":"Not Found","message":"Resource not found","timestamp":"%s"}"""
                .formatted(LocalDateTime.now());
        response.getWriter().write(body);
    }
}
