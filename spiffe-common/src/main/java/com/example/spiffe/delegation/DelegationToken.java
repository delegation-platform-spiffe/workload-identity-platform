package com.example.spiffe.delegation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a delegation token with user context and permissions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DelegationToken {
    /**
     * Subject (issuer) - typically the user service SPIFFE ID
     */
    private String subject;
    
    /**
     * Audience (target service) - the service this token is intended for
     */
    private String audience;
    
    /**
     * User ID that the token represents
     */
    private String userId;
    
    /**
     * Permissions granted by this delegation
     */
    private List<String> permissions;
    
    /**
     * Expiration time (Unix timestamp)
     */
    private long expiresAt;
    
    /**
     * Issued at time (Unix timestamp)
     */
    private long issuedAt;
}




