package com.example.userservice.controller;

import com.example.userservice.model.User;
import com.example.userservice.service.AuthService;
import com.example.userservice.service.DelegationService;
import com.example.userservice.service.UserTokenService;
import io.jsonwebtoken.Claims;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Authentication and delegation endpoints
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final DelegationService delegationService;
    private final UserTokenService userTokenService;
    
    /**
     * User login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<User> userOpt = authService.authenticate(request.getUsername(), request.getPassword());
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Issue a user JWT token for authentication
            String userToken = userTokenService.issueUserToken(user.getId(), user.getUsername(), null);
            return ResponseEntity.ok(Map.of(
                    "user_id", user.getId().toString(),
                    "username", user.getUsername(),
                    "access_token", userToken,
                    "token_type", "Bearer",
                    "message", "Login successful"
            ));
        } else {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }
    
    /**
     * Issue delegation token
     * Requires authentication via Bearer token in Authorization header
     */
    @PostMapping("/delegate")
    public ResponseEntity<?> delegate(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody DelegationRequest request) {
        try {
            // Validate authentication token
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Missing or invalid Authorization header. Expected: Bearer <token>"));
            }
            
            String token = authorization.substring(7); // Remove "Bearer " prefix
            Claims claims = userTokenService.validateUserToken(token);
            
            if (claims == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired authentication token"));
            }
            
            // Extract user ID from the authenticated token (not from request body for security)
            UUID authenticatedUserId = userTokenService.extractUserId(claims);
            if (authenticatedUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid user ID in authentication token"));
            }
            
            // Optionally validate that the request userId matches the authenticated user
            // This allows users to only delegate on their own behalf
            if (request.getUserId() != null && !request.getUserId().isEmpty()) {
                UUID requestedUserId;
                try {
                    requestedUserId = UUID.fromString(request.getUserId());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Invalid user_id format in request"));
                }
                
                if (!authenticatedUserId.equals(requestedUserId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Cannot delegate on behalf of another user"));
                }
            }
            
            // Use authenticated user ID
            UUID userId = authenticatedUserId;
            
            if (request.getTargetService() == null || request.getTargetService().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "target_service is required"));
            }
            
            String targetService = request.getTargetService();
            List<String> permissions = request.getPermissions() != null ? 
                    request.getPermissions() : List.of("read:photos");
            
            String targetServiceSpiffeId = "spiffe://example.org/" + targetService;
            String delegationToken = delegationService.issueDelegationToken(
                    userId, 
                    targetServiceSpiffeId, 
                    permissions,
                    request.getTtlSeconds()
            );
            
            log.info("Issued delegation token for authenticated user {} to service {}", userId, targetService);
            
            return ResponseEntity.ok(Map.of(
                    "delegation_token", delegationToken,
                    "expires_in", request.getTtlSeconds() != null ? request.getTtlSeconds() : 900
            ));
        } catch (IllegalArgumentException e) {
            log.error("Invalid request parameters", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to issue delegation token", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to issue delegation token: " + e.getMessage()));
        }
    }
    
    /**
     * Validate delegation token
     * Uses POST instead of GET to avoid exposing tokens in query parameters (security best practice)
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody ValidateTokenRequest request) {
        if (request.getToken() == null || request.getToken().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "error", "Token is required"
            ));
        }
        Map<String, Object> result = delegationService.validateToken(request.getToken());
        return ResponseEntity.ok(result);
    }
    
    /**
     * Register a new user
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            User user = authService.createUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword()
            );
            return ResponseEntity.status(201).body(Map.of(
                    "user_id", user.getId().toString(),
                    "username", user.getUsername(),
                    "message", "User registered successfully"
            ));
        } catch (Exception e) {
            log.error("Registration failed", e);
            return ResponseEntity.status(400).body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }
    
    @Data
    static class LoginRequest {
        private String username;
        private String password;
    }
    
    @Data
    static class DelegationRequest {
        private String userId;
        private String targetService;
        private List<String> permissions;
        private Integer ttlSeconds;
    }
    
    @Data
    static class RegisterRequest {
        private String username;
        private String email;
        private String password;
    }
    
    @Data
    static class ValidateTokenRequest {
        private String token;
    }
}

