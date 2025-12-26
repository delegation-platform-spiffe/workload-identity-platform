package com.example.spiffe.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for communicating with the Workload API service
 * to fetch certificates and manage service identity.
 */
@Slf4j
public class WorkloadApiClient {
    private final WebClient webClient;
    private final String workloadApiUrl;
    private final ObjectMapper objectMapper;
    
    public WorkloadApiClient(String workloadApiUrl) {
        this.workloadApiUrl = workloadApiUrl;
        this.webClient = WebClient.builder()
                .baseUrl(workloadApiUrl)
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Attest service identity and fetch certificate bundle
     * 
     * @param serviceName The name of the service (e.g., "photo-service")
     * @param attestationProof Proof of service identity (e.g., process ID, K8s SA token)
     * @return Certificate bundle with certificate, key, and CA chain
     */
    public Mono<CertificateBundle> fetchCertificate(String serviceName, Map<String, Object> attestationProof) {
        log.info("Fetching certificate for service: {}", serviceName);
        
        // First, attest the service identity
        return attest(serviceName, attestationProof)
                .flatMap(attestationToken -> {
                    // Then fetch the certificate bundle (pass service name as query param)
                    return webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                .path("/workload/v1/certificates")
                                .queryParam("service_name", serviceName)
                                .build())
                            .header("Authorization", "Bearer " + attestationToken)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .timeout(Duration.ofSeconds(10))
                            .map(this::parseCertificateBundle)
                            .doOnSuccess(bundle -> log.info("Successfully fetched certificate for {}", serviceName))
                            .doOnError(error -> log.error("Failed to fetch certificate for {}", serviceName, error));
                });
    }
    
    /**
     * Attest service identity
     */
    private Mono<String> attest(String serviceName, Map<String, Object> attestationProof) {
        Map<String, Object> request = Map.of(
                "service_name", serviceName,
                "attestation_proof", attestationProof
        );
        
        return webClient.post()
                .uri("/workload/v1/attest")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("token"))
                .timeout(Duration.ofSeconds(10));
    }
    
    /**
     * Parse certificate bundle from API response
     */
    private CertificateBundle parseCertificateBundle(Map<String, Object> response) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> svid = (Map<String, Object>) response.get("svid");
            String certPem = (String) svid.get("cert");
            String keyPem = (String) svid.get("key");
            String spiffeId = (String) svid.get("spiffe_id");
            
            @SuppressWarnings("unchecked")
            List<String> caCertsPem = (List<String>) response.get("ca_certs");
            String caCertPem = caCertsPem != null && !caCertsPem.isEmpty() ? caCertsPem.get(0) : null;
            
            Long expiresAtSeconds = ((Number) response.get("expires_at")).longValue();
            Long ttl = ((Number) response.get("ttl")).longValue();
            
            // Parse PEM strings to X509Certificate objects
            java.security.cert.CertificateFactory certFactory = 
                java.security.cert.CertificateFactory.getInstance("X.509");
            
            X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(
                new java.io.ByteArrayInputStream(certPem.getBytes()));
            
            List<X509Certificate> caChain = new java.util.ArrayList<>();
            if (caCertPem != null && !caCertPem.isEmpty()) {
                X509Certificate caCert = (X509Certificate) certFactory.generateCertificate(
                    new java.io.ByteArrayInputStream(caCertPem.getBytes()));
                caChain.add(caCert);
            }
            
            // Parse private key from PEM
            PrivateKey privateKey = parsePrivateKey(keyPem);
            
            CertificateBundle bundle = new CertificateBundle();
            bundle.setCertificate(certificate);
            bundle.setPrivateKey(privateKey);
            bundle.setCaChain(caChain);
            bundle.setSpiffeId(spiffeId);
            bundle.setExpiresAt(java.time.Instant.ofEpochSecond(expiresAtSeconds));
            bundle.setTtl(ttl);
            bundle.setCertificatePem(certPem);
            bundle.setPrivateKeyPem(keyPem);
            bundle.setCaCertificatePem(caCertPem);
            
            return bundle;
        } catch (Exception e) {
            log.error("Failed to parse certificate bundle", e);
            throw new RuntimeException("Failed to parse certificate bundle", e);
        }
    }
    
    /**
     * Parse private key from PEM string
     */
    private PrivateKey parsePrivateKey(String keyPem) throws Exception {
        // Remove PEM headers/footers
        String keyContent = keyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        
        byte[] keyBytes = java.util.Base64.getDecoder().decode(keyContent);
        
        // Try PKCS8 format first
        try {
            java.security.spec.PKCS8EncodedKeySpec keySpec = 
                new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            // Try PKCS1 format (legacy)
            try {
                org.bouncycastle.asn1.pkcs.RSAPrivateKey rsaKey = 
                    org.bouncycastle.asn1.pkcs.RSAPrivateKey.getInstance(keyBytes);
                java.security.spec.RSAPrivateKeySpec keySpec = 
                    new java.security.spec.RSAPrivateKeySpec(
                        rsaKey.getModulus(),
                        rsaKey.getPrivateExponent());
                java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
                return keyFactory.generatePrivate(keySpec);
            } catch (Exception e2) {
                throw new RuntimeException("Failed to parse private key", e2);
            }
        }
    }
}

