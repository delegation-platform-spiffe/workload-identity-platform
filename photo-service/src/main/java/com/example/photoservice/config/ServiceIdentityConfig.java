package com.example.photoservice.config;

import com.example.spiffe.identity.ServiceIdentityProvider;
import com.example.spiffe.mtls.MtlsClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;

/**
 * Configuration for service identity and mTLS
 */
@Slf4j
@Configuration
public class ServiceIdentityConfig {
    @Value("${workload-api.url:http://localhost:8080}")
    private String workloadApiUrl;
    
    @Value("${service.name:photo-service}")
    private String serviceName;
    
    private ServiceIdentityProvider identityProvider;
    
    @Bean
    public ServiceIdentityProvider serviceIdentityProvider() {
        // ⚠️ LOCAL DEVELOPMENT ONLY - Static token attestation
        // ⚠️ DO NOT USE IN PRODUCTION!
        // 
        // For local development, we use a static pre-shared token that must match
        // the token configured in the Workload API service.
        // 
        // In production, this should use proper attestation methods such as:
        // - Kubernetes Service Account token (read from /var/run/secrets/kubernetes.io/serviceaccount/token)
        // - AWS IAM role (from instance metadata)
        // - Process ID validation (verify process exists, command line, user, etc.)
        // - Unix domain socket ownership validation
        //
        // The token is provided via environment variable ATTESTATION_TOKEN
        // Default value matches the Workload API configuration for local dev
        String attestationToken = System.getenv("ATTESTATION_TOKEN");
        if (attestationToken == null || attestationToken.isEmpty()) {
            // Fallback to default dev token (matches application.yml in workload-api-service)
            attestationToken = "dev-token-photo-service-12345";
            log.warn("Using default attestation token for local development. " +
                    "Set ATTESTATION_TOKEN environment variable for custom token.");
        }
        
        Map<String, Object> attestationProof = Map.of(
            "service_name", serviceName,
            "process_id", ProcessHandle.current().pid(),
            "token", attestationToken  // Static token for local dev attestation
        );
        
        identityProvider = new ServiceIdentityProvider(serviceName, workloadApiUrl, attestationProof);
        
        // Initialize and fetch first certificate
        try {
            identityProvider.initialize();
            log.info("Service identity provider initialized for {}", serviceName);
        } catch (Exception e) {
            log.warn("Failed to initialize service identity provider: {}", e.getMessage());
            log.warn("Service will continue without mTLS support");
        }
        
        return identityProvider;
    }
    
    @Bean
    public RestTemplate mtlsRestTemplate(ServiceIdentityProvider identityProvider) {
        try {
            MtlsClientConfig mtlsClientConfig = new MtlsClientConfig(identityProvider);
            RestTemplate restTemplate = mtlsClientConfig.createMtlsRestTemplate();
            
            // Add SPIFFE identity header interceptor
            try {
                String spiffeId = identityProvider.getCurrentCertificate().getSpiffeId();
                if (spiffeId != null) {
                    restTemplate.getInterceptors().add(new com.example.spiffe.mtls.MtlsRequestInterceptor(spiffeId));
                }
            } catch (Exception e) {
                log.debug("Could not add SPIFFE identity header: {}", e.getMessage());
            }
            
            return restTemplate;
        } catch (Exception e) {
            log.warn("Failed to create mTLS RestTemplate: {}", e.getMessage());
            // Return regular RestTemplate if mTLS setup fails
            return new RestTemplate();
        }
    }
    
    @PreDestroy
    public void shutdown() {
        if (identityProvider != null) {
            identityProvider.shutdown();
        }
    }
}

