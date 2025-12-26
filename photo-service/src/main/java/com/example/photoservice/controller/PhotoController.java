package com.example.photoservice.controller;

import com.example.photoservice.model.Photo;
import com.example.photoservice.service.PhotoService;
import com.example.photoservice.service.PrintStatusService;
import com.example.spiffe.auth.AuthenticationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Photo management endpoints
 */
@Slf4j
@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
public class PhotoController {
    private final PhotoService photoService;
    private final PrintStatusService printStatusService;
    
    /**
     * Upload a photo
     * Requires authentication via Bearer token in Authorization header
     * User ID is extracted from the validated delegation token
     */
    @PostMapping
    public ResponseEntity<?> uploadPhoto(@RequestParam(value = "file", required = false) MultipartFile file) {
        // Get authenticated user ID from AuthenticationContext (set by AuthenticationFilter)
        String userIdStr = AuthenticationContext.getCurrentUserId();
        if (userIdStr == null || userIdStr.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required. Missing or invalid Authorization header."));
        }
        
        // Validate file parameter
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is required. Use 'file=@/path/to/image.jpg' in curl command."));
        }
        
        try {
            UUID userId = UUID.fromString(userIdStr);
            
            // Check if user has write permission
            AuthenticationContext authContext = AuthenticationContext.getCurrent();
            if (authContext != null && authContext.getPermissions() != null) {
                List<String> permissions = authContext.getPermissions();
                if (!permissions.contains("write:photos") && !permissions.contains("read:photos")) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Insufficient permissions. Required: write:photos or read:photos"));
                }
            }
            
            Photo photo = photoService.uploadPhoto(userId, file);
            log.info("Photo uploaded: {} for authenticated user {}", photo.getId(), userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(photo);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid user ID format"));
        } catch (Exception e) {
            log.error("Failed to upload photo", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload photo: " + e.getMessage()));
        }
    }
    
    /**
     * Get a photo by ID
     * Requires authentication via Bearer token in Authorization header
     * User ID is extracted from the validated delegation token
     */
    @GetMapping("/{photoId}")
    public ResponseEntity<?> getPhoto(@PathVariable String photoId) {
        // Get authenticated user ID from AuthenticationContext (set by AuthenticationFilter)
        String userIdStr = AuthenticationContext.getCurrentUserId();
        if (userIdStr == null || userIdStr.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required. Missing or invalid Authorization header."));
        }
        
        try {
            UUID photoUuid = UUID.fromString(photoId);
            UUID userId = UUID.fromString(userIdStr);
            
            // Check if user has read permission
            AuthenticationContext authContext = AuthenticationContext.getCurrent();
            if (authContext != null && authContext.getPermissions() != null) {
                List<String> permissions = authContext.getPermissions();
                if (!permissions.contains("read:photos")) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Insufficient permissions. Required: read:photos"));
                }
            }
            
            Optional<Photo> photoOpt = photoService.getPhoto(photoUuid, userId);
            
            if (photoOpt.isPresent()) {
                Photo photo = photoOpt.get();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(photo.getContentType()));
                headers.setContentLength(photo.getSize());
                headers.setContentDispositionFormData("attachment", photo.getFilename());
                
                log.info("Photo retrieved: {} for authenticated user {}", photoId, userId);
                return ResponseEntity.ok()
                        .headers(headers)
                        .body(photo.getPhotoBlob());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid photo ID or user ID format"));
        }
    }
    
    /**
     * Get all photos for a user
     * Requires authentication via Bearer token in Authorization header
     * User ID is extracted from the validated delegation token
     * The userId path parameter must match the authenticated user
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUserPhotos(@PathVariable String userId) {
        // Get authenticated user ID from AuthenticationContext (set by AuthenticationFilter)
        String authenticatedUserIdStr = AuthenticationContext.getCurrentUserId();
        if (authenticatedUserIdStr == null || authenticatedUserIdStr.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required. Missing or invalid Authorization header."));
        }
        
        try {
            UUID requestedUserId = UUID.fromString(userId);
            UUID authenticatedUserId = UUID.fromString(authenticatedUserIdStr);
            
            // Ensure user can only access their own photos
            if (!authenticatedUserId.equals(requestedUserId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Cannot access photos for another user"));
            }
            
            // Check if user has read permission
            AuthenticationContext authContext = AuthenticationContext.getCurrent();
            if (authContext != null && authContext.getPermissions() != null) {
                List<String> permissions = authContext.getPermissions();
                if (!permissions.contains("read:photos")) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Insufficient permissions. Required: read:photos"));
                }
            }
            
            List<Photo> photos = photoService.getUserPhotos(authenticatedUserId);
            log.info("Listed {} photos for authenticated user {}", photos.size(), authenticatedUserId);
            return ResponseEntity.ok(photos);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid user ID format"));
        }
    }
    
    /**
     * Check print status for a print job (machine-to-machine via mTLS)
     * This demonstrates photo-service calling print-service using mTLS with auto-renewing certificates
     */
    @GetMapping("/print-status/{printJobId}")
    public ResponseEntity<?> checkPrintStatus(@PathVariable String printJobId) {
        log.info("Checking print status for job {} via mTLS", printJobId);
        
        try {
            UUID jobId = UUID.fromString(printJobId);
            Map<String, Object> status = printStatusService.checkPrintStatus(jobId);
            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid print job ID"));
        } catch (Exception e) {
            log.error("Error checking print status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to check print status: " + e.getMessage()));
        }
    }
}

