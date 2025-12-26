package com.example.printservice.controller;

import com.example.printservice.model.PrintJob;
import com.example.printservice.service.PrintService;
import com.example.spiffe.auth.AuthenticationContext;
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
import java.util.stream.Collectors;

/**
 * Print service endpoints
 */
@Slf4j
@RestController
@RequestMapping("/print")
@RequiredArgsConstructor
public class PrintController {
    private final PrintService printService;
    
    /**
     * Create a print job
     * Requires authentication via Bearer token in Authorization header
     * User ID is extracted from the validated delegation token
     */
    @PostMapping
    public ResponseEntity<?> createPrintJob(@RequestBody PrintRequest request) {
        // Get authenticated user ID from AuthenticationContext (set by AuthenticationFilter)
        String authenticatedUserIdStr = AuthenticationContext.getCurrentUserId();
        if (authenticatedUserIdStr == null || authenticatedUserIdStr.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required. Missing or invalid Authorization header."));
        }
        
        try {
            UUID authenticatedUserId = UUID.fromString(authenticatedUserIdStr);
            
            // Optionally validate that the request userId matches the authenticated user
            if (request.getUserId() != null && !request.getUserId().isEmpty()) {
                UUID requestedUserId = UUID.fromString(request.getUserId());
                if (!authenticatedUserId.equals(requestedUserId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Cannot create print job for another user"));
                }
            }
            
            // Use authenticated user ID
            UUID userId = authenticatedUserId;
            
            // Validate photoIds
            if (request.getPhotoIds() == null || request.getPhotoIds().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "photoIds is required"));
            }
            
            // Check if user has print permission
            AuthenticationContext authContext = AuthenticationContext.getCurrent();
            if (authContext != null && authContext.getPermissions() != null) {
                List<String> permissions = authContext.getPermissions();
                if (!permissions.contains("print:photos") && !permissions.contains("read:photos")) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Insufficient permissions. Required: print:photos or read:photos"));
                }
            }
            
            List<UUID> photoIds = request.getPhotoIds().stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toList());
            
            PrintJob job = printService.createPrintJob(userId, photoIds);
            log.info("Print job created: {} for authenticated user {}", job.getId(), userId);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid user ID or photo ID format: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create print job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create print job: " + e.getMessage()));
        }
    }
    
    /**
     * Get print job status
     * Requires authentication via Bearer token in Authorization header
     * User ID is extracted from the validated delegation token
     * Users can only access their own print jobs
     */
    @GetMapping("/status/{printId}")
    public ResponseEntity<?> getPrintStatus(@PathVariable String printId) {
        // Get authenticated user ID from AuthenticationContext (set by AuthenticationFilter)
        String authenticatedUserIdStr = AuthenticationContext.getCurrentUserId();
        if (authenticatedUserIdStr == null || authenticatedUserIdStr.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required. Missing or invalid Authorization header."));
        }
        
        try {
            UUID jobId = UUID.fromString(printId);
            UUID authenticatedUserId = UUID.fromString(authenticatedUserIdStr);
            
            // Check if user has read permission
            AuthenticationContext authContext = AuthenticationContext.getCurrent();
            if (authContext != null && authContext.getPermissions() != null) {
                List<String> permissions = authContext.getPermissions();
                if (!permissions.contains("read:photos") && !permissions.contains("print:photos")) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Insufficient permissions. Required: read:photos or print:photos"));
                }
            }
            
            Optional<PrintJob> jobOpt = printService.getPrintJob(jobId);
            
            if (jobOpt.isPresent()) {
                PrintJob job = jobOpt.get();
                
                // Ensure user can only access their own print jobs
                if (!job.getUserId().equals(authenticatedUserId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Cannot access print job for another user"));
                }
                
                log.info("Returning print status for job {} for authenticated user {}", jobId, authenticatedUserId);
                return ResponseEntity.ok(job);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid print job ID format"));
        }
    }
    
    @Data
    static class PrintRequest {
        private String userId;
        private List<String> photoIds;
    }
}

