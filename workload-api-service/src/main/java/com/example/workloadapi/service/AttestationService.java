package com.example.workloadapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates service attestation and issues attestation tokens.
 * 
 * LOCAL DEVELOPMENT MODE:
 * Uses static pre-shared tokens for each service. Each service must provide
 * its unique token in the attestation_proof. This is INSECURE and should
 * ONLY be used for local development.
 * 
 * PRODUCTION MODE:
 * Should implement proper attestation validation such as:
 * - Kubernetes Service Account token validation
 * - Process ID and parent process validation
 * - Unix domain socket ownership validation
 * - AWS IAM role validation
 * 
 * NEVER use static tokens in production!
 */
@Service
public class AttestationService {
    private static final Logger log = LoggerFactory.getLogger(AttestationService.class);
    // Store attestation tokens (in production, use Redis or similar)
    private final Map<String, AttestationToken> tokens = new ConcurrentHashMap<>();
    
    // Static tokens for local development (INSECURE - DO NOT USE IN PRODUCTION)
    // In production, these should be null and proper attestation should be used
    private final Map<String, String> serviceTokens = new HashMap<>();
    
    /**
     * Initialize static tokens from configuration.
     * This is called by Spring after dependency injection.
     */
    public AttestationService(
            @Value("${attestation.tokens.photo-service:}") String photoServiceToken,
            @Value("${attestation.tokens.print-service:}") String printServiceToken,
            @Value("${attestation.tokens.user-service:}") String userServiceToken,
            @Value("${attestation.tokens.workload-api-service:}") String workloadApiServiceToken) {
        
        // Load static tokens for local development
        // WARNING: This is INSECURE and should ONLY be used for local development!
        // In production, these should be empty and proper attestation should be used.
        if (photoServiceToken != null && !photoServiceToken.isEmpty()) {
            serviceTokens.put("photo-service", photoServiceToken);
            log.info("Loaded static token for photo-service (LOCAL DEV ONLY)");
        }
        if (printServiceToken != null && !printServiceToken.isEmpty()) {
            serviceTokens.put("print-service", printServiceToken);
            log.info("Loaded static token for print-service (LOCAL DEV ONLY)");
        }
        if (userServiceToken != null && !userServiceToken.isEmpty()) {
            serviceTokens.put("user-service", userServiceToken);
            log.info("Loaded static token for user-service (LOCAL DEV ONLY)");
        }
        if (workloadApiServiceToken != null && !workloadApiServiceToken.isEmpty()) {
            serviceTokens.put("workload-api-service", workloadApiServiceToken);
            log.info("Loaded static token for workload-api-service (LOCAL DEV ONLY)");
        }
        
        if (serviceTokens.isEmpty()) {
            log.warn("No static tokens configured. Attestation will fail unless using production mode.");
        } else {
            log.warn("⚠️  USING STATIC TOKEN VALIDATION - LOCAL DEVELOPMENT ONLY!");
            log.warn("⚠️  DO NOT USE THIS IN PRODUCTION!");
        }
    }
    
    /**
     * Attest a service identity and issue an attestation token
     */
    public String attest(String serviceName, Map<String, Object> attestationProof) {
        log.info("Attesting service: {} with proof: {}", serviceName, attestationProof);
        
        // For local development, accept any attestation proof
        // In production, validate:
        // - Process ID and parent process
        // - Kubernetes service account token
        // - Unix domain socket ownership
        // - AWS IAM role, etc.
        
        if (!validateAttestation(serviceName, attestationProof)) {
            throw new IllegalArgumentException("Invalid attestation proof for service: " + serviceName);
        }
        
        // Issue attestation token (short-lived, 5 minutes)
        String token = UUID.randomUUID().toString();
        tokens.put(token, new AttestationToken(serviceName, System.currentTimeMillis() + 300000));
        
        log.info("Issued attestation token for service: {}", serviceName);
        return token;
    }
    
    /**
     * Validate attestation token
     */
    public boolean validateToken(String token, String expectedServiceName) {
        AttestationToken attToken = tokens.get(token);
        if (attToken == null) {
            return false;
        }
        
        if (System.currentTimeMillis() > attToken.getExpiresAt()) {
            tokens.remove(token);
            return false;
        }
        
        if (!attToken.getServiceName().equals(expectedServiceName)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Get service name from token
     */
    public String getServiceNameFromToken(String token) {
        AttestationToken attToken = tokens.get(token);
        if (attToken == null) {
            return null;
        }
        
        if (System.currentTimeMillis() > attToken.getExpiresAt()) {
            tokens.remove(token);
            return null;
        }
        
        return attToken.getServiceName();
    }
    
    /**
     * Validate attestation proof.
     * 
     * LOCAL DEVELOPMENT MODE:
     * Validates that the provided token matches the expected static token for the service.
     * 
     * PRODUCTION MODE:
     * Should validate using platform-specific attestation methods:
     * - Kubernetes: Validate Service Account token with K8s API
     * - AWS: Validate IAM role with AWS STS
     * - Process-based: Validate process ID, parent process, command line, user
     * - Unix sockets: Validate socket ownership
     */
    private boolean validateAttestation(String serviceName, Map<String, Object> proof) {
        if (proof == null || proof.isEmpty()) {
            log.warn("Empty attestation proof provided for service: {}", serviceName);
            return false;
        }
        
        // LOCAL DEVELOPMENT: Static token validation
        // Check if we have a static token configured for this service
        String expectedToken = serviceTokens.get(serviceName);
        if (expectedToken != null) {
            // Validate static token
            String providedToken = (String) proof.get("token");
            if (providedToken == null) {
                log.warn("No token provided in attestation proof for service: {}", serviceName);
                return false;
            }
            
            if (expectedToken.equals(providedToken)) {
                log.info("Static token validated for service: {}", serviceName);
                return true;
            } else {
                log.warn("Invalid static token provided for service: {}", serviceName);
                return false;
            }
        }
        
        // PRODUCTION MODE: Implement proper attestation validation here
        // For now, if no static token is configured, we reject the request
        // In production, this is where you would:
        // 1. Validate Kubernetes Service Account token
        // 2. Validate AWS IAM role
        // 3. Validate process ID and parent process
        // 4. Validate Unix socket ownership
        // etc.
        
        log.warn("No attestation method configured for service: {}. " +
                "Either configure static token (local dev) or implement production attestation.", serviceName);
        return false;
    }
    
    private static class AttestationToken {
        private final String serviceName;
        private final long expiresAt;
        
        public AttestationToken(String serviceName, long expiresAt) {
            this.serviceName = serviceName;
            this.expiresAt = expiresAt;
        }
        
        public String getServiceName() {
            return serviceName;
        }
        
        public long getExpiresAt() {
            return expiresAt;
        }
    }
}

