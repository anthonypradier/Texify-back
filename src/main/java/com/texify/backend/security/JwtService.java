package com.texify.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Handles JWT creation, validation, and revocation for the Texify auth flow.
 * <p>
 * Tokens are signed with HMAC-SHA256 using the key configured in
 * {@code jwt.secret} (Base64-encoded, min 256 bits). Expiration is driven by
 * {@code jwt.expiration} (milliseconds).
 * </p>
 * <p>
 * An in-memory blacklist enforces server-side logout. The blacklist is cleared
 * on application restart — replace with a Redis store when persistence or
 * horizontal scaling is required.
 * </p>
 */
@Service
@Slf4j
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    /** Holds tokens explicitly revoked via logout. */
    private final Set<String> blacklist = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Generates a signed JWT for the given user principal.
     *
     * @param userDetails the authenticated principal (email used as subject)
     * @return a compact, URL-safe JWT string
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(Map.of(), userDetails);
    }

    /**
     * Generates a signed JWT with additional custom claims.
     *
     * @param extraClaims additional claims to embed in the payload
     * @param userDetails the authenticated principal
     * @return a compact, URL-safe JWT string
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        String token = Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey())
                .compact();
        log.debug("JWT generated for subject '{}'", userDetails.getUsername());
        return token;
    }

    /**
     * Validates a token against the given principal and the blacklist.
     *
     * @param token       the JWT to validate
     * @param userDetails the principal the token must belong to
     * @return {@code true} if the token is syntactically valid, not expired,
     *         matches the principal, and has not been blacklisted
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        if (blacklist.contains(token)) {
            log.debug("Rejected blacklisted token for '{}'", userDetails.getUsername());
            return false;
        }
        try {
            String subject = extractUsername(token);
            return subject.equals(userDetails.getUsername()) && !isExpired(token);
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Adds a token to the blacklist so subsequent requests using it are rejected.
     * Called by the logout flow.
     *
     * @param token the JWT to revoke
     */
    public void revoke(String token) {
        blacklist.add(token);
        log.debug("Token revoked (blacklist size: {})", blacklist.size());
    }

    /**
     * Extracts the subject (email) from a token without re-validating the signature.
     *
     * @param token the JWT string
     * @return the email address embedded as the token subject
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts a single claim from the token payload.
     *
     * @param token    the JWT string
     * @param resolver a function mapping {@link Claims} to the desired value
     * @param <T>      type of the extracted claim
     * @return the claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parseAllClaims(token));
    }

    /**
     * Parses and verifies the token, returning its full claims payload.
     *
     * @param token the JWT string
     * @return the verified {@link Claims} object
     * @throws JwtException if the token is malformed, expired, or has an invalid signature
     */
    private Claims parseAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Checks whether the token's expiration timestamp has already passed.
     *
     * @param token the JWT string
     * @return {@code true} if the token is expired
     */
    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    /**
     * Decodes the Base64 secret and builds the HMAC-SHA256 signing key.
     *
     * @return the {@link SecretKey} used for signing and verification
     */
    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
