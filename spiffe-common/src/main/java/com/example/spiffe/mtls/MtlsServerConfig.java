package com.example.spiffe.mtls;

import com.example.spiffe.identity.CertificateBundle;
import com.example.spiffe.identity.ServiceIdentityProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for mTLS server (Spring Boot).
 * Configures HTTPS with client certificate authentication.
 * Note: For now, we'll keep HTTP and add mTLS as an optional HTTPS endpoint.
 * In production, you'd configure this via application properties.
 */
@Slf4j
public class MtlsServerConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    private final ServiceIdentityProvider identityProvider;
    private final int mtlsPort;
    
    public MtlsServerConfig(ServiceIdentityProvider identityProvider, int mtlsPort) {
        this.identityProvider = identityProvider;
        this.mtlsPort = mtlsPort;
    }
    
    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        try {
            CertificateBundle bundle = identityProvider.getCurrentCertificate();
            
            // Create key store with server certificate
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            List<X509Certificate> certChain = new ArrayList<>();
            
            // Get service certificate
            X509Certificate serviceCert;
            if (bundle.getCertificate() != null) {
                serviceCert = bundle.getCertificate();
            } else if (bundle.getCertificatePem() != null) {
                serviceCert = (X509Certificate) certFactory.generateCertificate(
                    new ByteArrayInputStream(bundle.getCertificatePem().getBytes()));
            } else {
                log.warn("No certificate available for mTLS server configuration");
                return;
            }
            certChain.add(serviceCert);
            
            // Add CA certificate if present
            if (bundle.getCaChain() != null && !bundle.getCaChain().isEmpty()) {
                certChain.addAll(bundle.getCaChain());
            } else if (bundle.getCaCertificatePem() != null && !bundle.getCaCertificatePem().isEmpty()) {
                X509Certificate caCert = (X509Certificate) certFactory.generateCertificate(
                    new ByteArrayInputStream(bundle.getCaCertificatePem().getBytes()));
                certChain.add(caCert);
            }
            
            Certificate[] certChainArray = certChain.toArray(new Certificate[0]);
            
            // Get private key
            PrivateKey privateKey = bundle.getPrivateKey();
            if (privateKey == null && bundle.getPrivateKeyPem() != null) {
                privateKey = parsePrivateKey(bundle.getPrivateKeyPem());
            }
            if (privateKey == null) {
                log.warn("No private key available for mTLS server configuration");
                return;
            }
            
            keyStore.setKeyEntry("server", privateKey, "".toCharArray(), certChainArray);
            
            // Create trust store with CA certificate for client validation
            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(null, null);
            
            if (bundle.getCaChain() != null && !bundle.getCaChain().isEmpty()) {
                trustStore.setCertificateEntry("ca", bundle.getCaChain().get(0));
            } else if (bundle.getCaCertificatePem() != null && !bundle.getCaCertificatePem().isEmpty()) {
                X509Certificate caCert = (X509Certificate) certFactory.generateCertificate(
                    new ByteArrayInputStream(bundle.getCaCertificatePem().getBytes()));
                trustStore.setCertificateEntry("ca", caCert);
            }
            
            // Create temporary key store file
            File keyStoreFile = createTempKeyStoreFile(keyStore);
            File trustStoreFile = createTempTrustStoreFile(trustStore);
            
            // Add HTTPS connector with mTLS
            factory.addConnectorCustomizers(connector -> {
                if (connector instanceof org.apache.catalina.connector.Connector) {
                    org.apache.catalina.connector.Connector httpsConnector = 
                        (org.apache.catalina.connector.Connector) connector;
                    
                    if (httpsConnector.getProtocol().contains("HTTP")) {
                        // Configure for HTTPS with mTLS
                        httpsConnector.setPort(mtlsPort);
                        httpsConnector.setSecure(true);
                        httpsConnector.setScheme("https");
                        httpsConnector.setProperty("SSLEnabled", "true");
                        httpsConnector.setProperty("sslProtocol", "TLS");
                        httpsConnector.setProperty("clientAuth", "true"); // Require client cert
                        httpsConnector.setProperty("keystoreFile", keyStoreFile.getAbsolutePath());
                        httpsConnector.setProperty("keystorePass", "");
                        httpsConnector.setProperty("keystoreType", "PKCS12");
                        httpsConnector.setProperty("truststoreFile", trustStoreFile.getAbsolutePath());
                        httpsConnector.setProperty("truststorePass", "");
                        httpsConnector.setProperty("truststoreType", "JKS");
                        
                        log.info("Configured mTLS server on port {} with client certificate authentication", mtlsPort);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to configure mTLS server", e);
            // Don't throw - allow service to start without mTLS if configuration fails
        }
    }
    
    /**
     * Create temporary key store file
     */
    private File createTempKeyStoreFile(KeyStore keyStore) throws Exception {
        File tempFile = File.createTempFile("keystore-", ".p12");
        tempFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            keyStore.store(fos, "".toCharArray());
        }
        return tempFile;
    }
    
    /**
     * Create temporary trust store file
     */
    private File createTempTrustStoreFile(KeyStore trustStore) throws Exception {
        File tempFile = File.createTempFile("truststore-", ".jks");
        tempFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            trustStore.store(fos, "".toCharArray());
        }
        return tempFile;
    }
    
    /**
     * Parse private key from PEM string
     */
    private PrivateKey parsePrivateKey(String keyPem) throws Exception {
        String keyContent = keyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        
        byte[] keyBytes = java.util.Base64.getDecoder().decode(keyContent);
        
        try {
            java.security.spec.PKCS8EncodedKeySpec keySpec = 
                new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
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

