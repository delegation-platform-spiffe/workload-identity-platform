package com.example.photoservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "photos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Photo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "photo_blob", nullable = false, columnDefinition = "BYTEA")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY)
    private byte[] photoBlob;
    
    @Column(name = "filename")
    private String filename;
    
    @Column(name = "content_type")
    private String contentType;
    
    @Column(name = "size")
    private Long size;
    
    @Column(columnDefinition = "TEXT")
    private String metadata;
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

