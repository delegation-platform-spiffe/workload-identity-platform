package com.example.spiffe.identity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

/**
 * Represents a certificate bundle containing the service certificate,
 * private key, CA chain, and metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CertificateBundle {
    /**
     * The service certificate (X.509)
     */
    private X509Certificate certificate;
    
    /**
     * The private key for the certificate (ephemeral, only in memory)
     */
    private PrivateKey privateKey;
    
    /**
     * The CA certificate chain
     */
    private List<X509Certificate> caChain;
    
    /**
     * The SPIFFE ID (e.g., spiffe://example.org/photo-service)
     */
    private String spiffeId;
    
    /**
     * When the certificate expires (Unix timestamp)
     */
    private Instant expiresAt;
    
    /**
     * Time to live in seconds
     */
    private long ttl;
    
    /**
     * PEM-encoded certificate (for compatibility)
     */
    private String certificatePem;
    
    /**
     * PEM-encoded private key (for compatibility)
     */
    private String privateKeyPem;
    
    /**
     * PEM-encoded CA certificate (for compatibility)
     */
    private String caCertificatePem;
    
    /**
     * Check if the certificate is expiring soon (within 20% of TTL remaining)
     */
    public boolean isExpiringSoon() {
        if (expiresAt == null) {
            return false;
        }
        long remainingSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return remainingSeconds < (ttl * 0.2);
    }
}

