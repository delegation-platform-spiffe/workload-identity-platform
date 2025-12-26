package com.example.printservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "print_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrintJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private UUID userId;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "print_job_photos", joinColumns = @JoinColumn(name = "print_job_id"))
    @Column(name = "photo_id")
    private List<UUID> photoIds = new ArrayList<>();
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PrintStatus status = PrintStatus.PENDING;
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column
    private Instant completedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    public enum PrintStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED
    }
}

