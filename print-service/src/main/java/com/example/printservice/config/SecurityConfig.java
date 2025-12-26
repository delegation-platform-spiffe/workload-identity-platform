package com.example.printservice.config;

import com.example.spiffe.auth.AuthenticationFilter;
import com.example.spiffe.delegation.DelegationTokenValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Security configuration for print-service
 * Registers the AuthenticationFilter to validate delegation tokens
 */
@Slf4j
@Configuration
public class SecurityConfig {
    
    @Value("${user-service.url:http://localhost:8081}")
    private String userServiceUrl;
    
    @Value("${delegation.signing-key:}")
    private String delegationSigningKey;
    
    /**
     * Register the authentication filter
     */
    @Bean
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilter() {
        // Create token validator (with optional signing key for local validation)
        DelegationTokenValidator tokenValidator;
        if (delegationSigningKey != null && !delegationSigningKey.isEmpty()) {
            tokenValidator = new DelegationTokenValidator(userServiceUrl, delegationSigningKey);
            log.info("Using DelegationTokenValidator with local signing key for faster validation");
        } else {
            tokenValidator = new DelegationTokenValidator(userServiceUrl);
            log.info("Using DelegationTokenValidator with remote validation only");
        }
        
        AuthenticationFilter filter = new AuthenticationFilter(tokenValidator, userServiceUrl);
        
        FilterRegistrationBean<AuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/print/*");
        registration.setOrder(1);
        
        log.info("Registered AuthenticationFilter for /print/* endpoints");
        return registration;
    }
}

