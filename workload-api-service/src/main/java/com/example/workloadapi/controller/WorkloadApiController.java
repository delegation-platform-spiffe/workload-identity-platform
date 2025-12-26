package com.example.workloadapi.controller;

import com.example.workloadapi.service.AttestationService;
import com.example.workloadapi.service.CertificateIssuanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Workload API REST endpoints for certificate issuance.
 */
@Slf4j
@RestController
@RequestMapping("/workload/v1")
@RequiredArgsConstructor
public class WorkloadApiController {
    private final AttestationService attestationService;
    private final CertificateIssuanceService certificateIssuanceService;
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }
    
    /**
     * Attest service identity
     */
    @PostMapping("/attest")
    public ResponseEntity<Map<String, String>> attest(@RequestBody Map<String, Object> request) {
        String serviceName = (String) request.get("service_name");
        @SuppressWarnings("unchecked")
        Map<String, Object> attestationProof = (Map<String, Object>) request.get("attestation_proof");
        
        String token = attestationService.attest(serviceName, attestationProof);
        
        return ResponseEntity.ok(Map.of("token", token));
    }
    
    /**
     * Fetch certificate bundle
     */
    @GetMapping("/certificates")
    public ResponseEntity<Map<String, Object>> getCertificates(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(value = "service_name", required = false) String serviceNameParam) {
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing or invalid Authorization header"));
        }
        
        String token = authHeader.substring(7);
        
        // Extract service name from token or use parameter
        String serviceName = extractServiceNameFromToken(token);
        if (serviceName == null && serviceNameParam != null) {
            serviceName = serviceNameParam;
        }
        
        if (serviceName == null) {
            return ResponseEntity.status(400).body(Map.of("error", "Service name required"));
        }
        
        try {
            Map<String, Object> bundle = certificateIssuanceService.issueCertificateBundle(serviceName, token);
            return ResponseEntity.ok(bundle);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }
    
    private String extractServiceNameFromToken(String token) {
        // In production, decode the attestation token to get service name
        // For now, AttestationService stores token -> service mapping
        // This is handled by AttestationService.validateToken which returns service name
        return attestationService.getServiceNameFromToken(token);
    }
}

