package com.okaynow.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates JWT access and refresh tokens. Refresh tokens carry a
 * "type" claim so they cannot be used as access tokens (and vice versa).
 */
@Component
public class JwtTokenProvider {

    public static final String TOKEN_TYPE_CLAIM = "type";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    public static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-validity-seconds:900}") long accessTokenValiditySeconds,
            @Value("${app.jwt.refresh-token-validity-seconds:1209600}") long refreshTokenValiditySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public String createAccessToken(UUID userId, String email, String role) {
        return createToken(userId, email, role, TOKEN_TYPE_ACCESS, accessTokenValiditySeconds);
    }

    public String createRefreshToken(UUID userId, String email, String role) {
        return createToken(userId, email, role, TOKEN_TYPE_REFRESH, refreshTokenValiditySeconds);
    }

    private String createToken(UUID userId, String email, String role, String type, long validitySeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim(ROLE_CLAIM, role)
                .claim(TOKEN_TYPE_CLAIM, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(validitySeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates the token signature/expiry. Throws JwtException if invalid.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TOKEN_TYPE_ACCESS.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TOKEN_TYPE_REFRESH.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public Claims parseClaimsOrNull(String token) {
        try {
            return parseClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }
}
