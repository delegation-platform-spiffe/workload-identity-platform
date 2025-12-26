package com.example.printservice.repository;

import com.example.printservice.model.PrintJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrintJobRepository extends JpaRepository<PrintJob, UUID> {
    List<PrintJob> findByUserId(UUID userId);
    Optional<PrintJob> findByIdAndUserId(UUID id, UUID userId);
}




