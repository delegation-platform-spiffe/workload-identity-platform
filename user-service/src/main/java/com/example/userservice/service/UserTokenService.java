package com.example.userservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Service for issuing and validating user authentication JWT tokens
 * These tokens are used to authenticate users when they call protected endpoints
 */
@Slf4j
@Service
public class UserTokenService {
    @Value("${user.token.signing-key:${delegation.signing-key:default-signing-key-change-in-production-min-256-bits}}")
    private String signingKeyBase64;
    
    @Value("${user.token.issuer:spiffe://example.org/user-service}")
    private String issuer;
    
    @Value("${user.token.default-ttl:3600}") // 1 hour
    private int defaultTtl;
    
    private SecretKey signingKey;
    
    private SecretKey getSigningKey() {
        if (signingKey == null) {
            signingKey = Keys.hmacShaKeyFor(signingKeyBase64.getBytes(StandardCharsets.UTF_8));
        }
        return signingKey;
    }
    
    /**
     * Issue a user authentication token
     */
    public String issueUserToken(UUID userId, String username, Integer ttlSeconds) {
        int ttl = ttlSeconds != null ? ttlSeconds : defaultTtl;
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttl);
        
        String token = Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .claim("username", username)
                .claim("user_id", userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(getSigningKey())
                .compact();
        
        log.info("Issued user token for user {} ({})", username, userId);
        return token;
    }
    
    /**
     * Validate and extract claims from a user token
     * @return Claims if valid, null if invalid
     */
    public Claims validateUserToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("User token validation failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract user ID from a validated token
     */
    public UUID extractUserId(Claims claims) {
        if (claims == null) {
            return null;
        }
        String userIdStr = claims.get("user_id", String.class);
        if (userIdStr == null) {
            userIdStr = claims.getSubject();
        }
        try {
            return UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid user_id in token: {}", userIdStr);
            return null;
        }
    }
}

