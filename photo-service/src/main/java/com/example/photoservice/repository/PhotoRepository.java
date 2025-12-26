package com.example.photoservice.repository;

import com.example.photoservice.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, UUID> {
    List<Photo> findByUserId(UUID userId);
    Optional<Photo> findByIdAndUserId(UUID id, UUID userId);
}




