package com.example.spiffe.mtls;

import com.example.spiffe.identity.CertificateBundle;
import com.example.spiffe.identity.ServiceIdentityProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for mTLS HTTP clients.
 * Automatically uses certificates from ServiceIdentityProvider and refreshes them.
 */
@Slf4j
public class MtlsClientConfig {
    private final ServiceIdentityProvider identityProvider;
    
    public MtlsClientConfig(ServiceIdentityProvider identityProvider) {
        this.identityProvider = identityProvider;
    }
    
    /**
     * Create a RestTemplate configured for mTLS
     */
    public RestTemplate createMtlsRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(createMtlsRequestFactory());
        return restTemplate;
    }
    
    /**
     * Create an HTTP request factory with mTLS
     */
    public ClientHttpRequestFactory createMtlsRequestFactory() {
        try {
            SSLContext sslContext = createSslContext();
            
            org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient = 
                org.apache.hc.client5.http.impl.classic.HttpClients.custom()
                    .setConnectionManager(org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder.create()
                            .setSslContext(sslContext)
                            .build())
                        .build())
                    .evictIdleConnections(org.apache.hc.core5.util.TimeValue.ofSeconds(30))
                    .evictExpiredConnections()
                    .build();
            
            return new HttpComponentsClientHttpRequestFactory(httpClient);
        } catch (Exception e) {
            log.error("Failed to create mTLS request factory", e);
            throw new RuntimeException("Failed to create mTLS request factory", e);
        }
    }
    
    /**
     * Create SSL context with client certificate and CA trust store
     */
    private SSLContext createSslContext() throws Exception {
        CertificateBundle bundle = identityProvider.getCurrentCertificate();
        
        // Create key store with client certificate and private key
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        
        // Add client certificate chain
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certChain = new ArrayList<>();
        
        // Add service certificate (use PEM if X509Certificate not available)
        X509Certificate serviceCert;
        if (bundle.getCertificate() != null) {
            serviceCert = bundle.getCertificate();
        } else if (bundle.getCertificatePem() != null) {
            serviceCert = (X509Certificate) certFactory.generateCertificate(
                new ByteArrayInputStream(bundle.getCertificatePem().getBytes()));
        } else {
            throw new IllegalStateException("No certificate available in bundle");
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
            throw new IllegalStateException("No private key available in bundle");
        }
        
        keyStore.setKeyEntry("client", privateKey, "".toCharArray(), certChainArray);
        
        // Create trust store with CA certificate
        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(null, null);
        
        if (bundle.getCaChain() != null && !bundle.getCaChain().isEmpty()) {
            trustStore.setCertificateEntry("ca", bundle.getCaChain().get(0));
        } else if (bundle.getCaCertificatePem() != null && !bundle.getCaCertificatePem().isEmpty()) {
            X509Certificate caCert = (X509Certificate) certFactory.generateCertificate(
                new ByteArrayInputStream(bundle.getCaCertificatePem().getBytes()));
            trustStore.setCertificateEntry("ca", caCert);
        }
        
        // Create trust manager
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
        trustManagerFactory.init(trustStore);
        
        // Create key manager
        javax.net.ssl.KeyManagerFactory keyManagerFactory = 
            javax.net.ssl.KeyManagerFactory.getInstance("PKIX");
        keyManagerFactory.init(keyStore, "".toCharArray());
        
        // Create SSL context
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
            keyManagerFactory.getKeyManagers(),
            trustManagerFactory.getTrustManagers(),
            null
        );
        
        log.debug("Created SSL context with client certificate for service: {}", 
            bundle.getSpiffeId());
        
        return sslContext;
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
            // Try PKCS1 format (legacy) using BouncyCastle
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

