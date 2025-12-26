package com.example.printservice.config;

import com.example.spiffe.identity.ServiceIdentityProvider;
import com.example.spiffe.mtls.MtlsServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    
    @Value("${service.name:print-service}")
    private String serviceName;
    
    @Value("${server.mtls.port:8443}")
    private int mtlsPort;
    
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
            attestationToken = "dev-token-print-service-67890";
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
    public TomcatServletWebServerFactory tomcatServletWebServerFactory(ServiceIdentityProvider identityProvider) {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        
        // Configure mTLS if identity provider is available
        if (identityProvider != null) {
            try {
                MtlsServerConfig mtlsConfig = new MtlsServerConfig(identityProvider, mtlsPort);
                mtlsConfig.customize(factory);
            } catch (Exception e) {
                log.warn("Failed to configure mTLS server: {}", e.getMessage());
            }
        }
        
        return factory;
    }
    
    @PreDestroy
    public void shutdown() {
        if (identityProvider != null) {
            identityProvider.shutdown();
        }
    }
}

