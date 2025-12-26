package com.example.spiffe.auth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Thread-local context for authenticated requests.
 * Holds information about the authenticated user or service.
 */
public class AuthenticationContext {
    private static final ThreadLocal<AuthenticationContext> context = new ThreadLocal<>();
    
    @Getter
    @Setter
    private String userId;
    
    @Getter
    @Setter
    private List<String> permissions;
    
    @Getter
    @Setter
    private String serviceIdentity; // SPIFFE ID for service-to-service auth
    
    @Getter
    @Setter
    private String delegationToken; // Original delegation token if present
    
    /**
     * Get the current authentication context for this thread
     */
    public static AuthenticationContext getCurrent() {
        return context.get();
    }
    
    /**
     * Set the authentication context for this thread
     */
    public static void setCurrent(AuthenticationContext authContext) {
        context.set(authContext);
    }
    
    /**
     * Clear the authentication context for this thread
     */
    public static void clear() {
        context.remove();
    }
    
    /**
     * Check if the current request is authenticated
     */
    public static boolean isAuthenticated() {
        AuthenticationContext ctx = getCurrent();
        return ctx != null && (ctx.getUserId() != null || ctx.getServiceIdentity() != null);
    }
    
    /**
     * Get the current user ID (if user is authenticated)
     */
    public static String getCurrentUserId() {
        AuthenticationContext ctx = getCurrent();
        return ctx != null ? ctx.getUserId() : null;
    }
    
    /**
     * Get the current service identity (if service is authenticated)
     */
    public static String getCurrentServiceIdentity() {
        AuthenticationContext ctx = getCurrent();
        return ctx != null ? ctx.getServiceIdentity() : null;
    }
}




