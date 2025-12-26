package com.example.spiffe.mtls;

import lombok.extern.slf4j.Slf4j;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts SPIFFE identity from X.509 certificates.
 * SPIFFE identity is typically in the Subject Alternative Name (SAN) as a URI.
 */
@Slf4j
public class SpiffeIdentityExtractor {
    private static final Pattern SPIFFE_URI_PATTERN = Pattern.compile("spiffe://([^/]+)/(.+)");
    
    /**
     * Extract SPIFFE identity from certificate
     * @param certificate The X.509 certificate
     * @return SPIFFE ID (e.g., "spiffe://example.org/photo-service") or null if not found
     */
    public static String extractSpiffeId(X509Certificate certificate) {
        try {
            // Check Subject Alternative Name extension
            // SPIFFE identity is typically in the URI SAN
            byte[] sanBytes = certificate.getExtensionValue("2.5.29.17"); // Subject Alternative Name OID
            
            if (sanBytes != null) {
                // Parse ASN.1 structure to find URI entries
                String sanString = new String(sanBytes);
                
                // Look for SPIFFE URI pattern
                Matcher matcher = SPIFFE_URI_PATTERN.matcher(sanString);
                if (matcher.find()) {
                    String spiffeId = matcher.group(0);
                    log.debug("Extracted SPIFFE ID from certificate: {}", spiffeId);
                    return spiffeId;
                }
            }
            
            // Fallback: Check Subject DN for SPIFFE-like patterns
            X500Principal subject = certificate.getSubjectX500Principal();
            String subjectName = subject.getName();
            
            // Look for SPIFFE URI in subject name
            Matcher matcher = SPIFFE_URI_PATTERN.matcher(subjectName);
            if (matcher.find()) {
                String spiffeId = matcher.group(0);
                log.debug("Extracted SPIFFE ID from Subject DN: {}", spiffeId);
                return spiffeId;
            }
            
            log.warn("No SPIFFE ID found in certificate. Subject: {}", subjectName);
            return null;
        } catch (Exception e) {
            log.error("Failed to extract SPIFFE ID from certificate", e);
            return null;
        }
    }
    
    /**
     * Extract SPIFFE identity from certificate chain (checks all certificates)
     */
    public static String extractSpiffeId(X509Certificate[] certificateChain) {
        if (certificateChain == null || certificateChain.length == 0) {
            return null;
        }
        
        // Check the first certificate (client/service certificate)
        String spiffeId = extractSpiffeId(certificateChain[0]);
        if (spiffeId != null) {
            return spiffeId;
        }
        
        // If not found, check other certificates in chain
        for (int i = 1; i < certificateChain.length; i++) {
            spiffeId = extractSpiffeId(certificateChain[i]);
            if (spiffeId != null) {
                return spiffeId;
            }
        }
        
        return null;
    }
    
    /**
     * Validate SPIFFE ID format
     */
    public static boolean isValidSpiffeId(String spiffeId) {
        if (spiffeId == null || spiffeId.isEmpty()) {
            return false;
        }
        return SPIFFE_URI_PATTERN.matcher(spiffeId).matches();
    }
}




