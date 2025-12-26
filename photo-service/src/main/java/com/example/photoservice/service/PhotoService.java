package com.example.photoservice.service;

import com.example.photoservice.model.Photo;
import com.example.photoservice.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing photos
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {
    private final PhotoRepository photoRepository;
    
    /**
     * Upload a photo for a user
     */
    public Photo uploadPhoto(UUID userId, MultipartFile file) {
        try {
            Photo photo = new Photo();
            photo.setUserId(userId);
            photo.setPhotoBlob(file.getBytes());
            photo.setFilename(file.getOriginalFilename());
            photo.setContentType(file.getContentType());
            photo.setSize(file.getSize());
            
            Photo saved = photoRepository.save(photo);
            log.info("Photo uploaded: {} for user {}", saved.getId(), userId);
            return saved;
        } catch (Exception e) {
            log.error("Failed to upload photo", e);
            throw new RuntimeException("Failed to upload photo", e);
        }
    }
    
    /**
     * Get a photo by ID (with user validation)
     */
    public Optional<Photo> getPhoto(UUID photoId, UUID userId) {
        return photoRepository.findByIdAndUserId(photoId, userId);
    }
    
    /**
     * Get all photos for a user
     */
    public List<Photo> getUserPhotos(UUID userId) {
        return photoRepository.findByUserId(userId);
    }
}




