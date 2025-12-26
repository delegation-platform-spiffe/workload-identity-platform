package com.example.spiffe.delegation;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Issues delegation tokens (JWTs) that allow services to act on behalf of users.
 */
@Slf4j
public class DelegationTokenIssuer {
    private final SecretKey signingKey;
    private final String issuerSpiffeId; // e.g., spiffe://example.org/user-service
    
    public DelegationTokenIssuer(String signingKeyBase64, String issuerSpiffeId) {
        this.signingKey = Keys.hmacShaKeyFor(signingKeyBase64.getBytes(StandardCharsets.UTF_8));
        this.issuerSpiffeId = issuerSpiffeId;
    }
    
    /**
     * Issue a delegation token for a user to delegate to a target service
     * 
     * @param userId The user ID
     * @param targetServiceSpiffeId The SPIFFE ID of the target service
     * @param permissions List of permissions to grant
     * @param ttlSeconds Time to live in seconds (default: 900 = 15 minutes)
     * @return JWT token string
     */
    public String issueToken(String userId, 
                             String targetServiceSpiffeId, 
                             List<String> permissions,
                             int ttlSeconds) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        
        String token = Jwts.builder()
                .subject(issuerSpiffeId)
                .issuer(issuerSpiffeId)
                .audience().add(targetServiceSpiffeId).and()
                .claim("user_id", userId)
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        
        log.info("Issued delegation token for user {} to service {}", userId, targetServiceSpiffeId);
        return token;
    }
}




