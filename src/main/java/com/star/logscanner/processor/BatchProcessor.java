package com.star.logscanner.processor;

import com.star.logscanner.entity.LogEntry;
import com.star.logscanner.repository.LogEntryRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Utility for efficient batch processing of log entries.
 * 
 * <p>Features:
 * <ul>
 *   <li>Configurable batch sizes</li>
 *   <li>Automatic flushing when batch is full</li>
 *   <li>Progress callbacks</li>
 *   <li>Partial failure handling</li>
 *   <li>Statistics tracking</li>
 * </ul>
 * 
 * <p>Usage:
 * <pre>{@code
 * BatchProcessor processor = BatchProcessor.builder()
 *     .batchSize(1000)
 *     .repository(logEntryRepository)
 *     .onBatchComplete((stats) -> updateProgress(stats))
 *     .build();
 * 
 * for (LogEntry entry : entries) {
 *     processor.add(entry);
 * }
 * processor.flush(); // Flush remaining entries
 * 
 * BatchStatistics stats = processor.getStatistics();
 * }</pre>
 * 
 * @author LogScanner Team
 * @version 2.0
 */
@Slf4j
public class BatchProcessor {
    
    private final int batchSize;
    private final LogEntryRepository repository;
    private final Consumer<BatchStatistics> onBatchComplete;
    private final boolean continueOnError;
    
    private final List<LogEntry> currentBatch;
    /**
     * -- GETTER --
     *  Get batch processing statistics.
     *
     * @return statistics object
     */
    @Getter
    private final BatchStatistics statistics;
    
    public static class BatchStatistics {
        private final AtomicLong totalEntries = new AtomicLong(0);
        private final AtomicLong savedEntries = new AtomicLong(0);
        private final AtomicLong failedEntries = new AtomicLong(0);
        private final AtomicLong batchesProcessed = new AtomicLong(0);
        private final AtomicLong totalSaveTimeMs = new AtomicLong(0);
        
        public long getTotalEntries() { return totalEntries.get(); }
        public long getSavedEntries() { return savedEntries.get(); }
        public long getFailedEntries() { return failedEntries.get(); }
        public long getBatchesProcessed() { return batchesProcessed.get(); }
        public long getTotalSaveTimeMs() { return totalSaveTimeMs.get(); }
        
        public double getSuccessRate() {
            long total = totalEntries.get();
            return total > 0 ? (savedEntries.get() * 100.0) / total : 0;
        }
        
        public double getAverageSaveTimeMs() {
            long batches = batchesProcessed.get();
            return batches > 0 ? (double) totalSaveTimeMs.get() / batches : 0;
        }
        
        void recordBatch(int entriesAttempted, int entriesSaved, long saveTimeMs) {
            totalEntries.addAndGet(entriesAttempted);
            savedEntries.addAndGet(entriesSaved);
            failedEntries.addAndGet(entriesAttempted - entriesSaved);
            batchesProcessed.incrementAndGet();
            totalSaveTimeMs.addAndGet(saveTimeMs);
        }
        
        public void reset() {
            totalEntries.set(0);
            savedEntries.set(0);
            failedEntries.set(0);
            batchesProcessed.set(0);
            totalSaveTimeMs.set(0);
        }
        
        @Override
        public String toString() {
            return String.format(
                    "BatchStatistics{total=%d, saved=%d, failed=%d, batches=%d, avgTimeMs=%.2f}",
                    totalEntries.get(), savedEntries.get(), failedEntries.get(),
                    batchesProcessed.get(), getAverageSaveTimeMs());
        }
    }
    
    private BatchProcessor(Builder builder) {
        this.batchSize = builder.batchSize;
        this.repository = builder.repository;
        this.onBatchComplete = builder.onBatchComplete;
        this.continueOnError = builder.continueOnError;
        this.currentBatch = new ArrayList<>(batchSize);
        this.statistics = new BatchStatistics();
    }
    
    /**
     * Add an entry to the batch. Automatically flushes when batch is full.
     * 
     * @param entry the entry to add
     */
    public void add(LogEntry entry) {
        if (entry == null) {
            return;
        }
        
        currentBatch.add(entry);
        
        if (currentBatch.size() >= batchSize) {
            flush();
        }
    }
    
    /**
     * Add multiple entries to the batch.
     * 
     * @param entries the entries to add
     */
    public void addAll(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        
        for (LogEntry entry : entries) {
            add(entry);
        }
    }
    
    /**
     * Flush the current batch to storage.
     * 
     * @return number of entries successfully saved
     */
    public int flush() {
        if (currentBatch.isEmpty()) {
            return 0;
        }
        
        int batchCount = currentBatch.size();
        int savedCount = 0;
        long startTime = System.currentTimeMillis();
        
        try {
            savedCount = saveBatch(new ArrayList<>(currentBatch));
            log.debug("Flushed batch of {} entries ({} saved)", batchCount, savedCount);
        } catch (Exception e) {
            log.error("Failed to flush batch of {} entries: {}", batchCount, e.getMessage());
            if (!continueOnError) {
                throw new RuntimeException("Batch save failed", e);
            }
        } finally {
            long saveTime = System.currentTimeMillis() - startTime;
            statistics.recordBatch(batchCount, savedCount, saveTime);
            currentBatch.clear();
            
            // Notify callback
            if (onBatchComplete != null) {
                try {
                    onBatchComplete.accept(statistics);
                } catch (Exception e) {
                    log.warn("Batch complete callback failed: {}", e.getMessage());
                }
            }
        }
        
        return savedCount;
    }
    
    /**
     * Get the current batch size (pending entries).
     * 
     * @return number of entries in current batch
     */
    public int getCurrentBatchSize() {
        return currentBatch.size();
    }
    
    /**
     * Check if batch is empty.
     * 
     * @return true if no pending entries
     */
    public boolean isEmpty() {
        return currentBatch.isEmpty();
    }

    public void reset() {
        currentBatch.clear();
        statistics.reset();
    }
    
    private int saveBatch(List<LogEntry> batch) {
        if (repository == null) {
            log.warn("No repository configured, batch not saved");
            return 0;
        }
        
        try {
            repository.saveAll(batch);
            return batch.size();
        } catch (Exception e) {
            log.error("Batch save failed: {}", e.getMessage());
            
            // Try saving individually to salvage what we can
            if (continueOnError) {
                return saveIndividually(batch);
            }
            throw e;
        }
    }
    
    private int saveIndividually(List<LogEntry> batch) {
        int saved = 0;
        for (LogEntry entry : batch) {
            try {
                repository.save(entry);
                saved++;
            } catch (Exception e) {
                log.debug("Failed to save entry {}: {}", entry.getId(), e.getMessage());
            }
        }
        log.info("Individual save recovered {}/{} entries", saved, batch.size());
        return saved;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int batchSize = 1000;
        private LogEntryRepository repository;
        private Consumer<BatchStatistics> onBatchComplete;
        private boolean continueOnError = true;
        
        /**
         * Set the batch size.
         * 
         * @param batchSize entries per batch
         * @return this builder
         */
        public Builder batchSize(int batchSize) {
            if (batchSize < 1) {
                throw new IllegalArgumentException("Batch size must be positive");
            }
            this.batchSize = batchSize;
            return this;
        }
        
        /**
         * Set the repository for saving entries.
         * 
         * @param repository the LogEntryRepository
         * @return this builder
         */
        public Builder repository(LogEntryRepository repository) {
            this.repository = repository;
            return this;
        }
        
        /**
         * Set callback for batch completion.
         * 
         * @param callback callback receiving statistics
         * @return this builder
         */
        public Builder onBatchComplete(Consumer<BatchStatistics> callback) {
            this.onBatchComplete = callback;
            return this;
        }
        
        /**
         * Set whether to continue processing on errors.
         * 
         * @param continueOnError true to continue
         * @return this builder
         */
        public Builder continueOnError(boolean continueOnError) {
            this.continueOnError = continueOnError;
            return this;
        }
        
        /**
         * Build the BatchProcessor.
         * 
         * @return new BatchProcessor instance
         */
        public BatchProcessor build() {
            return new BatchProcessor(this);
        }
    }
}
