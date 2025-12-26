package com.example.userservice.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for issuing and validating delegation tokens
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DelegationService {
    @Value("${delegation.signing-key:default-signing-key-change-in-production-min-256-bits}")
    private String signingKeyBase64;
    
    @Value("${delegation.issuer-spiffe-id:spiffe://example.org/user-service}")
    private String issuerSpiffeId;
    
    @Value("${delegation.default-ttl:900}") // 15 minutes
    private int defaultTtl;
    
    private SecretKey signingKey;
    
    private SecretKey getSigningKey() {
        if (signingKey == null) {
            signingKey = Keys.hmacShaKeyFor(signingKeyBase64.getBytes(StandardCharsets.UTF_8));
        }
        return signingKey;
    }
    
    /**
     * Issue a delegation token
     */
    public String issueDelegationToken(UUID userId, 
                                       String targetServiceSpiffeId, 
                                       List<String> permissions,
                                       Integer ttlSeconds) {
        int ttl = ttlSeconds != null ? ttlSeconds : defaultTtl;
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttl);
        
        String token = Jwts.builder()
                .subject(issuerSpiffeId)
                .issuer(issuerSpiffeId)
                .audience().add(targetServiceSpiffeId).and()
                .claim("user_id", userId.toString())
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(getSigningKey())
                .compact();
        
        log.info("Issued delegation token for user {} to service {}", userId, targetServiceSpiffeId);
        return token;
    }
    
    /**
     * Validate a delegation token
     */
    public Map<String, Object> validateToken(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            Map<String, Object> result = Map.of(
                    "valid", true,
                    "token", Map.of(
                            "user_id", claims.get("user_id", String.class),
                            "permissions", claims.get("permissions", List.class),
                            "audience", claims.getAudience(),
                            "expires_at", claims.getExpiration().getTime() / 1000
                    )
            );
            
            return result;
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return Map.of(
                    "valid", false,
                    "error", "Invalid or expired token"
            );
        }
    }
}




