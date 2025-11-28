package com.star.logscanner.service;

import com.star.logscanner.dto.JobStatusEnum;
import com.star.logscanner.entity.JobResult;
import com.star.logscanner.entity.JobStatus;
import com.star.logscanner.entity.LogAnalysisResult;
import com.star.logscanner.entity.LogEntry;
import com.star.logscanner.exception.LogProcessingException;
import com.star.logscanner.parser.*;
import com.star.logscanner.processor.BatchProcessor;
import com.star.logscanner.processor.FileStreamProcessor;
import com.star.logscanner.repository.JobStatusRepository;
import com.star.logscanner.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for orchestrating log file processing.
 * 
 * <p>This service is responsible ONLY for:
 * <ul>
 *   <li>Managing the processing workflow</li>
 *   <li>Job status updates</li>
 *   <li>File I/O and streaming coordination</li>
 *   <li>Batch processing and Elasticsearch storage</li>
 *   <li>Async execution and error handling</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogProcessingService {

    private final LogEntryRepository logEntryRepository;
    private final JobStatusRepository jobStatusRepository;
    private final LogParserFactory logParserFactory;

    @Value("${app.processing.batch-size:1000}")
    private int batchSize;

    @Value("${app.processing.buffer-size:8192}")
    private int bufferSize;

    @Value("${app.file.temp-directory:/tmp/logscanner}")
    private String tempDirectory;

    @Async("taskExecutor")
    public void processLogFileAsync(String jobId, MultipartFile logfile, 
                                    Path tempFile, String timestampFormat) {
        long startTime = System.currentTimeMillis();
        String fileName = logfile.getOriginalFilename();

        JobStatus jobStatus = initializeJobStatus(jobId, logfile, timestampFormat);

        try {
            log.info("Starting job {} for file: {} ({} bytes)", 
                    jobId, fileName, logfile.getSize());
            
            updateJobStatus(jobId, JobStatusEnum.PROCESSING, 0, "Initializing processing");

            LogAnalysisResult result = processFile(tempFile, jobId, fileName, timestampFormat);

            long processingTime = System.currentTimeMillis() - startTime;
            double linesPerSecond = result.getTotalLines() > 0 
                    ? (result.getTotalLines() * 1000.0) / processingTime 
                    : 0;

            completeJob(jobStatus, result, processingTime, linesPerSecond);

            log.info("Job {} completed. {} lines in {} ms ({} lines/sec)",
                    jobId, result.getTotalLines(), processingTime, 
                    String.format("%.2f", linesPerSecond));

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            failJob(jobStatus, e);
            throw new LogProcessingException("Failed to process log file", e);
            
        } finally {
            cleanupTempFile(tempFile);
        }
    }

    private LogAnalysisResult processFile(Path filePath, String jobId, 
                                          String fileName, String timestampFormat) 
            throws IOException {
        
        LogParser parser = logParserFactory.getParserForFile(filePath);
        log.info("Using {} parser for file: {}", parser.getSupportedFormat(), fileName);

        ParseContext context = ParseContext.builder()
                .jobId(jobId)
                .fileName(fileName)
                .timestampFormat(timestampFormat)
                .build();

        parser.reset();

        AtomicLong totalLines = new AtomicLong(0);
        AtomicLong successfulLines = new AtomicLong(0);
        AtomicLong failedLines = new AtomicLong(0);
        LogEntry[] previousEntry = {null}; // Array for lambda capture

        BatchProcessor batchProcessor = BatchProcessor.builder()
                .batchSize(batchSize)
                .repository(logEntryRepository)
                .continueOnError(true)
                .onBatchComplete(stats -> {
                    int progress = calculateProgress(stats.getTotalEntries(), totalLines.get());
                    updateJobStatus(jobId, JobStatusEnum.PROCESSING, progress,
                            String.format("Processed %d entries", stats.getTotalEntries()));
                })
                .build();

        FileStreamProcessor fileProcessor = FileStreamProcessor.builder()
                .bufferSize(bufferSize)
                .onProgress((current, total) -> {
                    int progress = calculateProgress(current, total);
                    updateJobStatus(jobId, JobStatusEnum.PROCESSING, progress,
                            String.format("Reading line %d/%d", current, total));
                })
                .build();

        totalLines.set(fileProcessor.countLines(filePath));
        updateJobStatus(jobId, JobStatusEnum.PROCESSING, 5, 
                "Counted " + totalLines.get() + " lines");

        fileProcessor.processFile(filePath, (line, lineNumber) -> {
            ParseResult result = parser.parseLine(line, lineNumber, context);
            
            processParseResult(result, previousEntry, batchProcessor, 
                    successfulLines, failedLines, context);
        });

        if (parser.supportsMultiLine() && parser instanceof TextLogParser textParser) {
            LogEntry pending = textParser.flushPending();
            if (pending != null) {
                pending.setJobId(jobId);
                pending.setFileName(fileName);
                batchProcessor.add(pending);
                successfulLines.incrementAndGet();
            }
        }

        batchProcessor.flush();

        LogAnalysisResult analysisResult = new LogAnalysisResult();
        analysisResult.setTotalLines((int) totalLines.get());
        analysisResult.setProcessedLines((int) totalLines.get());
        analysisResult.setSuccessfulLines((int) successfulLines.get());
        analysisResult.setFailedLines((int) failedLines.get());

        calculateStatistics(jobId, analysisResult);

        updateJobStatus(jobId, JobStatusEnum.PROCESSING, 95, "Finalizing analysis");

        return analysisResult;
    }

    private void processParseResult(ParseResult result, LogEntry[] previousEntry,
                                    BatchProcessor batchProcessor,
                                    AtomicLong successfulLines, AtomicLong failedLines,
                                    ParseContext context) {
        switch (result.getStatus()) {
            case SUCCESS -> {
                LogEntry entry = result.getEntry();
                batchProcessor.add(entry);
                previousEntry[0] = entry;
                successfulLines.incrementAndGet();
            }
            
            case CONTINUATION -> {
                // Append to previous entry's stack trace
                if (previousEntry[0] != null && result.getRawLine() != null) {
                    appendStackTrace(previousEntry[0], result.getRawLine());
                }
            }
            
            case FAILED -> {
                failedLines.incrementAndGet();
                log.debug("Parse failed at line {}: {}", 
                        result.getLineNumber(), result.getErrorMessage());
            }
            
            case BUFFERED -> {
                // Entry is being assembled (multi-line)
                // Nothing to do here - parser handles it internally
            }
            
            case SKIPPED -> {
                // Empty line, header, etc.
                log.trace("Skipped line {}: {}", 
                        result.getLineNumber(), result.getAdditionalInfo());
            }
        }
    }

    // Append a stack trace line to an existing entry.
    private void appendStackTrace(LogEntry entry, String line) {
        if (entry.getStackTrace() == null) {
            entry.setStackTrace(line);
        } else {
            entry.setStackTrace(entry.getStackTrace() + "\n" + line);
        }
        entry.setHasStackTrace(true);
    }

    private JobStatus initializeJobStatus(String jobId, MultipartFile logfile,
                                          String timestampFormat) {
        JobStatus jobStatus = JobStatus.builder()
                .jobId(jobId)
                .status(JobStatusEnum.QUEUED)
                .progress(0)
                .message("Job queued for processing")
                .fileName(logfile.getOriginalFilename())
                .fileSize(logfile.getSize())
                .timestampFormat(timestampFormat)
                .startedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        jobStatusRepository.save(jobStatus);
        return jobStatus;
    }

    private void updateJobStatus(String jobId, JobStatusEnum status, 
                                 int progress, String message) {
        try {
            JobStatus jobStatus = jobStatusRepository.findByJobId(jobId).orElse(null);
            if (jobStatus != null) {
                jobStatus.setStatus(status);
                jobStatus.setProgress(progress);
                jobStatus.setMessage(message);
                jobStatus.setUpdatedAt(LocalDateTime.now());
                jobStatusRepository.save(jobStatus);
            }
        } catch (Exception e) {
            log.error("Failed to update job status for {}: {}", jobId, e.getMessage());
        }
    }

    private void completeJob(JobStatus jobStatus, LogAnalysisResult result,
                             long processingTime, double linesPerSecond) {
        jobStatus = jobStatusRepository.findByJobId(jobStatus.getJobId()).orElse(jobStatus);
        jobStatus.setStatus(JobStatusEnum.COMPLETED);
        jobStatus.setProgress(100);
        jobStatus.setMessage("Processing completed successfully");
        jobStatus.setCompletedAt(LocalDateTime.now());
        jobStatus.setUpdatedAt(LocalDateTime.now());
        jobStatus.setTotalLines((long) result.getTotalLines());
        jobStatus.setProcessedLines((long) result.getProcessedLines());
        jobStatus.setSuccessfulLines((long) result.getSuccessfulLines());
        jobStatus.setFailedLines((long) result.getFailedLines());
        jobStatus.setProcessingTimeMs(processingTime);
        jobStatus.setLinesPerSecond(linesPerSecond);
        jobStatusRepository.save(jobStatus);
    }

    private void failJob(JobStatus jobStatus, Exception e) {
        jobStatus = jobStatusRepository.findByJobId(jobStatus.getJobId()).orElse(jobStatus);
        jobStatus.setStatus(JobStatusEnum.FAILED);
        jobStatus.setMessage("Processing failed: " + e.getMessage());
        jobStatus.setError(e.getMessage());
        jobStatus.setCompletedAt(LocalDateTime.now());
        jobStatus.setUpdatedAt(LocalDateTime.now());
        jobStatusRepository.save(jobStatus);
    }

    private void calculateStatistics(String jobId, LogAnalysisResult result) {
        try {
            long totalCount = logEntryRepository.countByJobId(jobId);
            long errorCount = logEntryRepository.countByJobIdAndHasErrorTrue(jobId);

            Map<String, Long> levelCounts = new HashMap<>();
            for (String level : Arrays.asList("ERROR", "WARN", "INFO", "DEBUG", "TRACE")) {
                long count = logEntryRepository.countByJobIdAndLevel(jobId, level);
                if (count > 0) {
                    levelCounts.put(level, count);
                }
            }

            result.setTotalEntries(totalCount);
            result.setErrorCount(errorCount);
            result.setLevelCounts(levelCounts);

        } catch (Exception e) {
            log.warn("Failed to calculate statistics for job {}: {}", jobId, e.getMessage());
        }
    }

    private int calculateProgress(long current, long total) {
        if (total <= 0) return 0;
        // Reserve 5% for startup and 5% for finalization
        return 5 + (int) ((current * 90) / total);
    }

    public JobStatus getJobStatus(String jobId) {
        return jobStatusRepository.findByJobId(jobId).orElse(null);
    }

    public JobResult getJobResult(String jobId) {
        JobStatus status = getJobStatus(jobId);
        if (status == null || status.getStatus() != JobStatusEnum.COMPLETED) {
            return null;
        }

        LogAnalysisResult analysisResult = new LogAnalysisResult();
        analysisResult.setTotalLines(
                status.getTotalLines() != null ? status.getTotalLines().intValue() : 0);
        analysisResult.setProcessedLines(
                status.getProcessedLines() != null ? status.getProcessedLines().intValue() : 0);
        analysisResult.setSuccessfulLines(
                status.getSuccessfulLines() != null ? status.getSuccessfulLines().intValue() : 0);
        analysisResult.setFailedLines(
                status.getFailedLines() != null ? status.getFailedLines().intValue() : 0);

        Map<String, Long> levelCounts = new HashMap<>();
        for (String level : Arrays.asList("ERROR", "WARN", "INFO", "DEBUG", "TRACE")) {
            long count = logEntryRepository.countByJobIdAndLevel(jobId, level);
            if (count > 0) {
                levelCounts.put(level, count);
            }
        }
        analysisResult.setLevelCounts(levelCounts);

        return new JobResult(analysisResult);
    }

    public Path saveTemporaryFile(MultipartFile file) throws IOException {
        Path tempDir = Files.createTempDirectory("logscanner-");

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            originalFilename = "upload.tmp";
        }

        String fileExtension = originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                : ".tmp";

        Path tempFile = Files.createTempFile(tempDir, "log-", fileExtension);

        try {
            file.transferTo(tempFile.toFile());
            log.info("Saved temporary file: {} ({} bytes)", tempFile, file.getSize());
            return tempFile;
        } catch (IOException e) {
            cleanupTempFile(tempFile);
            try {
                Files.deleteIfExists(tempDir);
            } catch (IOException deleteError) {
                log.warn("Failed to cleanup temp directory", deleteError);
            }
            throw new IOException("Failed to save temporary file: " + originalFilename, e);
        }
    }

    private void cleanupTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
                // Also try to delete parent temp directory
                Path parent = tempFile.getParent();
                if (parent != null && parent.toString().contains("logscanner-")) {
                    Files.deleteIfExists(parent);
                }
                log.debug("Cleaned up temporary file: {}", tempFile);
            } catch (IOException e) {
                log.warn("Failed to delete temporary file: {}", tempFile, e);
            }
        }
    }

    public Map<String, String> getAvailableParsers() {
        return logParserFactory.getParserInfo();
    }
}
