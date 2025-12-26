package com.example.spiffe.identity;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages service identity certificates fetched from Workload API.
 * Handles automatic certificate rotation and in-memory storage.
 */
@Slf4j
public class ServiceIdentityProvider {
    private final String serviceName;
    private final WorkloadApiClient workloadApiClient;
    private final Map<String, Object> attestationProof;
    
    private volatile CertificateBundle currentBundle;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private ScheduledExecutorService refreshScheduler;
    
    public ServiceIdentityProvider(String serviceName, String workloadApiUrl, Map<String, Object> attestationProof) {
        this.serviceName = serviceName;
        this.workloadApiClient = new WorkloadApiClient(workloadApiUrl);
        this.attestationProof = attestationProof;
        this.refreshScheduler = Executors.newScheduledThreadPool(1);
    }
    
    /**
     * Initialize and fetch the first certificate
     */
    public void initialize() {
        log.info("Initializing service identity provider for {}", serviceName);
        refreshCertificate();
    }
    
    /**
     * Get the current certificate bundle.
     * If expiring soon, triggers a refresh.
     */
    public CertificateBundle getCurrentCertificate() {
        lock.readLock().lock();
        try {
            if (currentBundle == null || currentBundle.isExpiringSoon()) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    // Double-check after acquiring write lock
                    if (currentBundle == null || currentBundle.isExpiringSoon()) {
                        refreshCertificate();
                    }
                } finally {
                    lock.writeLock().unlock();
                    lock.readLock().lock();
                }
            }
            return currentBundle;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Refresh the certificate from Workload API
     */
    private void refreshCertificate() {
        log.info("Refreshing certificate for service: {}", serviceName);
        
        try {
            CertificateBundle newBundle = workloadApiClient
                    .fetchCertificate(serviceName, attestationProof)
                    .block(); // Blocking call - in production, consider async
            
            if (newBundle != null) {
                // Clear old private key from memory
                if (currentBundle != null && currentBundle.getPrivateKey() != null) {
                    clearPrivateKey(currentBundle.getPrivateKey());
                }
                
                // Store new bundle in memory
                currentBundle = newBundle;
                
                // Schedule next refresh at 80% of TTL
                long refreshDelay = (long) (newBundle.getTtl() * 0.8);
                refreshScheduler.schedule(this::refreshCertificate, refreshDelay, TimeUnit.SECONDS);
                
                log.info("Certificate refreshed for {}. Next refresh in {} seconds", serviceName, refreshDelay);
            }
        } catch (Exception e) {
            log.error("Failed to refresh certificate for {}", serviceName, e);
            // Schedule retry in 30 seconds
            refreshScheduler.schedule(this::refreshCertificate, 30, TimeUnit.SECONDS);
        }
    }
    
    /**
     * Clear private key from memory (best effort)
     */
    private void clearPrivateKey(java.security.PrivateKey privateKey) {
        // In a real implementation, we would attempt to clear the key material
        // This is a placeholder - actual key clearing is complex in Java
        log.debug("Clearing private key from memory");
    }
    
    /**
     * Shutdown and cleanup
     */
    public void shutdown() {
        log.info("Shutting down service identity provider for {}", serviceName);
        if (currentBundle != null && currentBundle.getPrivateKey() != null) {
            clearPrivateKey(currentBundle.getPrivateKey());
        }
        currentBundle = null;
        refreshScheduler.shutdown();
    }
}




