package com.example.photoservice.service;

// Note: In a real implementation, we'd have a shared DTO or use the print-service client library
// For now, we'll use Map to avoid circular dependencies
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Service for checking print status via mTLS.
 * Uses auto-renewing certificates from ServiceIdentityProvider.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintStatusService {
    private final RestTemplate mtlsRestTemplate;
    
    @Value("${print-service.mtls-url:https://print-service:8443}")
    private String printServiceMtlsUrl;
    
    @Value("${print-service.url:http://print-service:8083}")
    private String printServiceUrl;
    
    /**
     * Check print job status using mTLS
     * This demonstrates machine-to-machine communication with certificate authentication.
     * Certificates are automatically renewed by ServiceIdentityProvider.
     */
    public Map<String, Object> checkPrintStatus(UUID printJobId) {
        try {
            log.info("Checking print status for job {} via mTLS", printJobId);
            
            // Try mTLS first, fallback to HTTP if mTLS not configured
            String url = printServiceMtlsUrl + "/print/status/" + printJobId;
            try {
                ResponseEntity<Map> response = mtlsRestTemplate.getForEntity(url, Map.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    log.info("Successfully retrieved print status via mTLS");
                    return response.getBody();
                }
            } catch (Exception mTlsError) {
                log.warn("mTLS call failed, falling back to HTTP: {}", mTlsError.getMessage());
                // Fallback to HTTP
                url = printServiceUrl + "/print/status/" + printJobId;
                ResponseEntity<Map> response = mtlsRestTemplate.getForEntity(url, Map.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> result = response.getBody();
                    result.put("note", "Retrieved via HTTP (mTLS not yet configured)");
                    return result;
                }
            }
            
            log.warn("Failed to retrieve print status");
            return Map.of("error", "Failed to retrieve print status");
        } catch (Exception e) {
            log.error("Error checking print status", e);
            return Map.of("error", "Failed to check print status: " + e.getMessage());
        }
    }
}

