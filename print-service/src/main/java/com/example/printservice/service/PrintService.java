package com.example.printservice.service;

import com.example.printservice.model.PrintJob;
import com.example.printservice.repository.PrintJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing print jobs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintService {
    private final PrintJobRepository printJobRepository;
    
    /**
     * Create a print job
     */
    public PrintJob createPrintJob(UUID userId, List<UUID> photoIds) {
        PrintJob job = new PrintJob();
        job.setUserId(userId);
        job.setPhotoIds(photoIds);
        job.setStatus(PrintJob.PrintStatus.PENDING);
        
        PrintJob saved = printJobRepository.save(job);
        log.info("Created print job: {} for user {}", saved.getId(), userId);
        
        // Simulate printing process
        processPrintJob(saved);
        
        return saved;
    }
    
    /**
     * Get print job status
     */
    public Optional<PrintJob> getPrintJob(UUID jobId) {
        return printJobRepository.findById(jobId);
    }
    
    /**
     * Simulate print job processing
     */
    private void processPrintJob(PrintJob job) {
        // In a real implementation, this would trigger actual printing
        // For now, just simulate the process
        new Thread(() -> {
            try {
                job.setStatus(PrintJob.PrintStatus.IN_PROGRESS);
                printJobRepository.save(job);
                
                Thread.sleep(2000); // Simulate printing time
                
                job.setStatus(PrintJob.PrintStatus.COMPLETED);
                job.setCompletedAt(Instant.now());
                printJobRepository.save(job);
                
                log.info("Print job {} completed", job.getId());
            } catch (InterruptedException e) {
                job.setStatus(PrintJob.PrintStatus.FAILED);
                printJobRepository.save(job);
                log.error("Print job {} failed", job.getId(), e);
            }
        }).start();
    }
}




