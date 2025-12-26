package com.example.spiffe.auth;

import com.example.spiffe.delegation.DelegationTokenValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Spring Web filter that validates delegation tokens from Authorization header
 * and sets the AuthenticationContext.
 */
@Slf4j
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {
    private final DelegationTokenValidator tokenValidator;
    private final String userServiceUrl; // For token validation
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        // Skip authentication for certain paths
        if (shouldSkipAuthentication(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No Authorization header found for {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Missing or invalid Authorization header\"}");
            return;
        }
        
        String token = authHeader.substring(7); // Remove "Bearer " prefix
        
        try {
            // Validate the delegation token
            DelegationTokenValidator.TokenValidationResult validationResult = 
                    tokenValidator.validate(token, userServiceUrl);
            
            if (validationResult.isValid()) {
                // Set authentication context
                AuthenticationContext authContext = new AuthenticationContext();
                authContext.setUserId(validationResult.getUserId());
                authContext.setPermissions(validationResult.getPermissions());
                authContext.setDelegationToken(token);
                AuthenticationContext.setCurrent(authContext);
                
                log.debug("Authenticated request for user: {}", validationResult.getUserId());
                filterChain.doFilter(request, response);
            } else {
                log.warn("Invalid token: {}", validationResult.getError());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"" + validationResult.getError() + "\"}");
            }
        } catch (Exception e) {
            log.error("Error validating token", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Internal server error\"}");
        } finally {
            // Always clear context after request
            AuthenticationContext.clear();
        }
    }
    
    private boolean shouldSkipAuthentication(String uri) {
        // Skip authentication for health checks, metrics, etc.
        return uri.startsWith("/actuator") || 
               uri.startsWith("/health") || 
               uri.equals("/");
    }
}




