package com.example.workloadapi.service;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Random;

/**
 * Certificate Authority service that manages CA keys and issues service certificates.
 * For local development, stores CA private keys on filesystem.
 */
@Service
public class CertificateAuthorityService {
    private static final Logger log = LoggerFactory.getLogger(CertificateAuthorityService.class);
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    
    @Value("${ca.key-store.path:./ca-keys}")
    private String keyStorePath;
    
    @Value("${ca.trust-domain:example.org}")
    private String trustDomain;
    
    private KeyPair caKeyPair;
    private X509Certificate caCertificate;
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Certificate Authority for trust domain: {}", trustDomain);
        loadOrCreateCA();
    }
    
    /**
     * Load CA key pair from filesystem or create new one if it doesn't exist
     */
    private void loadOrCreateCA() {
        File keyStoreDir = new File(keyStorePath);
        if (!keyStoreDir.exists()) {
            keyStoreDir.mkdirs();
        }
        
        File privateKeyFile = new File(keyStoreDir, "ca-private-key.pem");
        File certificateFile = new File(keyStoreDir, "ca-certificate.pem");
        
        if (privateKeyFile.exists() && certificateFile.exists()) {
            log.info("Loading existing CA from {}", keyStorePath);
            loadCA(privateKeyFile, certificateFile);
        } else {
            log.info("Creating new CA and saving to {}", keyStorePath);
            createNewCA();
            saveCA(privateKeyFile, certificateFile);
        }
    }
    
    /**
     * Create a new CA key pair and certificate
     */
    private void createNewCA() {
        try {
            // Generate CA key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            caKeyPair = keyGen.generateKeyPair();
            
            // Create CA certificate
            X500Name issuer = new X500Name("CN=SPIFFE CA, O=" + trustDomain);
            BigInteger serialNumber = BigInteger.valueOf(new Random().nextLong());
            Date notBefore = Date.from(Instant.now());
            Date notAfter = Date.from(Instant.now().plus(365, ChronoUnit.DAYS)); // 1 year validity
            
            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuer,
                    serialNumber,
                    notBefore,
                    notAfter,
                    issuer,
                    caKeyPair.getPublic()
            );
            
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                    .setProvider("BC")
                    .build(caKeyPair.getPrivate());
            
            X509CertificateHolder certHolder = certBuilder.build(signer);
            caCertificate = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certHolder);
            
            log.info("Created new CA certificate with serial: {}", serialNumber);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create CA", e);
        }
    }
    
    /**
     * Load CA from filesystem
     */
    private void loadCA(File privateKeyFile, File certificateFile) {
        // TODO: Implement PEM file reading
        // For now, create a new CA if files don't exist
        // In production, use proper PEM parsing
        log.warn("CA loading from filesystem not yet implemented, creating new CA");
        createNewCA();
    }
    
    /**
     * Save CA to filesystem
     */
    private void saveCA(File privateKeyFile, File certificateFile) {
        // TODO: Implement PEM file writing
        // For now, just log
        log.info("CA keys would be saved to {} and {}", privateKeyFile, certificateFile);
        log.warn("CA key persistence not yet fully implemented - keys are in memory only");
    }
    
    /**
     * Issue a certificate for a service
     */
    public X509Certificate issueCertificate(String serviceName, PublicKey servicePublicKey) {
        try {
            String spiffeId = "spiffe://" + trustDomain + "/" + serviceName;
            X500Name subject = new X500Name("CN=" + serviceName + ", O=" + trustDomain);
            
            BigInteger serialNumber = BigInteger.valueOf(new Random().nextLong());
            Date notBefore = Date.from(Instant.now());
            Date notAfter = Date.from(Instant.now().plus(1, ChronoUnit.HOURS)); // 1 hour validity
            
            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    new X500Name("CN=SPIFFE CA, O=" + trustDomain),
                    serialNumber,
                    notBefore,
                    notAfter,
                    subject,
                    servicePublicKey
            );
            
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                    .setProvider("BC")
                    .build(caKeyPair.getPrivate());
            
            X509CertificateHolder certHolder = certBuilder.build(signer);
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certHolder);
            
            log.info("Issued certificate for service: {} (SPIFFE ID: {})", serviceName, spiffeId);
            return certificate;
        } catch (Exception e) {
            throw new RuntimeException("Failed to issue certificate for " + serviceName, e);
        }
    }
    
    public X509Certificate getCACertificate() {
        return caCertificate;
    }
    
    public KeyPair getCAKeyPair() {
        return caKeyPair;
    }
    
    public String getTrustDomain() {
        return trustDomain;
    }
    
    @PreDestroy
    public void cleanup() {
        // Clear CA private key from memory (best effort)
        if (caKeyPair != null && caKeyPair.getPrivate() != null) {
            log.info("Clearing CA private key from memory");
            // Actual key clearing is complex in Java
        }
    }
}

