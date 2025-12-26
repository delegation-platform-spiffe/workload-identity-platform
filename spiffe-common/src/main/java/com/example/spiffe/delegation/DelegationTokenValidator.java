package com.example.spiffe.delegation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Validates delegation tokens by either:
 * 1. Validating locally with signing key (if available)
 * 2. Validating remotely with User Service
 */
@Slf4j
public class DelegationTokenValidator {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;
    private SecretKey signingKey; // Optional - for local validation
    
    public DelegationTokenValidator(String userServiceUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }
    
    public DelegationTokenValidator(String userServiceUrl, String signingKeyBase64) {
        this(userServiceUrl);
        if (signingKeyBase64 != null) {
            this.signingKey = Keys.hmacShaKeyFor(signingKeyBase64.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    /**
     * Validate a delegation token
     */
    public TokenValidationResult validate(String token, String userServiceUrl) {
        // Try local validation first if signing key is available
        if (signingKey != null) {
            try {
                return validateLocally(token);
            } catch (Exception e) {
                log.debug("Local validation failed, trying remote validation", e);
            }
        }
        
        // Fall back to remote validation
        return validateRemotely(token, userServiceUrl);
    }
    
    /**
     * Validate token locally using signing key
     */
    private TokenValidationResult validateLocally(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        String userId = claims.get("user_id", String.class);
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.get("permissions");
        
        return new TokenValidationResult(true, userId, permissions, null);
    }
    
    /**
     * Validate token remotely via User Service
     * Uses POST to avoid exposing tokens in query parameters (security best practice)
     */
    private TokenValidationResult validateRemotely(String token, String userServiceUrl) {
        try {
            Map<String, String> requestBody = Map.of("token", token);
            
            Map<String, Object> response = webClient.post()
                    .uri("/auth/validate")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            
            if (response != null && Boolean.TRUE.equals(response.get("valid"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> tokenData = (Map<String, Object>) response.get("token");
                String userId = (String) tokenData.get("user_id");
                @SuppressWarnings("unchecked")
                List<String> permissions = (List<String>) tokenData.get("permissions");
                
                return new TokenValidationResult(true, userId, permissions, null);
            } else {
                String error = response != null ? (String) response.get("error") : "Invalid token";
                return new TokenValidationResult(false, null, null, error);
            }
        } catch (Exception e) {
            log.error("Error validating token remotely", e);
            return new TokenValidationResult(false, null, null, "Validation error: " + e.getMessage());
        }
    }
    
    @Data
    @AllArgsConstructor
    public static class TokenValidationResult {
        private boolean valid;
        private String userId;
        private List<String> permissions;
        private String error;
    }
}




