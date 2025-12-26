package com.example.workloadapi.service;

import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/**
 * Issues certificate bundles for services.
 */
@Service
@RequiredArgsConstructor
public class CertificateIssuanceService {
    private static final Logger log = LoggerFactory.getLogger(CertificateIssuanceService.class);
    private final CertificateAuthorityService caService;
    private final AttestationService attestationService;
    
    /**
     * Issue a certificate bundle for a service
     */
    public Map<String, Object> issueCertificateBundle(String serviceName, String attestationToken) {
        // Validate attestation token
        if (!attestationService.validateToken(attestationToken, serviceName)) {
            throw new IllegalArgumentException("Invalid or expired attestation token");
        }
        
        // Generate service key pair
        KeyPair serviceKeyPair = generateKeyPair();
        
        // Issue certificate
        X509Certificate certificate = caService.issueCertificate(serviceName, serviceKeyPair.getPublic());
        
        // Build certificate bundle response
        Map<String, Object> bundle = new HashMap<>();
        Map<String, String> svid = new HashMap<>();
        svid.put("cert", certificateToPem(certificate));
        svid.put("key", privateKeyToPem(serviceKeyPair.getPrivate()));
        svid.put("spiffe_id", "spiffe://" + caService.getTrustDomain() + "/" + serviceName);
        
        bundle.put("svid", svid);
        bundle.put("ca_certs", new String[]{certificateToPem(caService.getCACertificate())});
        bundle.put("expires_at", certificate.getNotAfter().getTime() / 1000);
        bundle.put("ttl", 3600); // 1 hour
        
        log.info("Issued certificate bundle for service: {}", serviceName);
        return bundle;
    }
    
    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate key pair", e);
        }
    }
    
    private String certificateToPem(X509Certificate cert) {
        try {
            StringWriter sw = new StringWriter();
            PemWriter pw = new PemWriter(sw);
            PemObject pemObject = new PemObject("CERTIFICATE", cert.getEncoded());
            pw.writeObject(pemObject);
            pw.close();
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert certificate to PEM", e);
        }
    }
    
    private String privateKeyToPem(PrivateKey key) {
        try {
            StringWriter sw = new StringWriter();
            PemWriter pw = new PemWriter(sw);
            PemObject pemObject = new PemObject("PRIVATE KEY", key.getEncoded());
            pw.writeObject(pemObject);
            pw.close();
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert private key to PEM", e);
        }
    }
}


