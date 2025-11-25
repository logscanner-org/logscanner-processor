package com.star.logscanner.service;

import com.star.logscanner.dto.JobStatusEnum;
import com.star.logscanner.entity.JobResult;
import com.star.logscanner.entity.JobStatus;
import com.star.logscanner.entity.LogAnalysisResult;
import com.star.logscanner.entity.LogEntry;
import com.star.logscanner.exception.LogProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

@Service
@Slf4j
public class LogProcessingService {

    private final Map<String, JobStatus> jobStatuses = new ConcurrentHashMap<>();
    private final Map<String, JobResult> jobResults = new ConcurrentHashMap<>();

    @Async("taskExecutor")
    public void processLogFileAsync(String jobId, MultipartFile logfile, String timestampFormat) {

        JobStatus initialStatus = new JobStatus(
                jobId,
                JobStatusEnum.QUEUED,
                0,
                "Job queued for processing",
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null
        );
        jobStatuses.put(jobId, initialStatus);

        try {
            updateJobStatus(jobId, JobStatusEnum.PROCESSING, 0, "Starting file processing");

            // Save file temporarily
            Path tempFile = Files.createTempFile("log-", ".tmp");
            logfile.transferTo(tempFile.toFile());

            log.info("Processing log file for job {}: {}", jobId, logfile.getOriginalFilename());

            // Process file in chunks
            LogAnalysisResult result = processLogFile(tempFile, timestampFormat,
                    (progress, message) -> updateJobStatus(jobId, JobStatusEnum.PROCESSING, progress, message));

            // Store result
            jobResults.put(jobId, new JobResult(result));

            JobStatus completedStatus = jobStatuses.get(jobId);
            completedStatus.setStatus(JobStatusEnum.COMPLETED);
            completedStatus.setProgress(100);
            completedStatus.setMessage("Processing completed successfully");
            completedStatus.setCompletedAt(LocalDateTime.now());
            completedStatus.setUpdatedAt(LocalDateTime.now());

            log.info("Job {} completed successfully", jobId);

            // Clean up temp file
            Files.deleteIfExists(tempFile);

        } catch (Exception e) {
            log.error("Job {} failed with error: {}", jobId, e.getMessage(), e);

            JobStatus failedStatus = jobStatuses.get(jobId);
            failedStatus.setStatus(JobStatusEnum.FAILED);
            failedStatus.setMessage("Processing failed");
            failedStatus.setError(e.getMessage());
            failedStatus.setCompletedAt(LocalDateTime.now());
            failedStatus.setUpdatedAt(LocalDateTime.now());

            throw new LogProcessingException("Failed to process log file", e);
        }
    }

    private LogAnalysisResult processLogFile(Path filePath, String timestampFormat,
                                             BiConsumer<Integer, String> progressCallback) throws IOException {

        LogAnalysisResult result = new LogAnalysisResult();
        long totalLines = 0;
        long processedLines = 0;

        // First pass: count lines
        try (Stream<String> lines = Files.lines(filePath)) {
            totalLines = lines.count();
        }

        progressCallback.accept(5, "Counted " + totalLines + " lines");

        // Second pass: process
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            int batchSize = 1000;
            List<String> batch = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                batch.add(line);
                processedLines++;

                if (batch.size() >= batchSize) {
                    processBatch(batch, timestampFormat, result);
                    batch.clear();

                    int progress = 5 + (int) ((processedLines * 90) / totalLines);
                    progressCallback.accept(progress,
                            String.format("Processed %d/%d lines", processedLines, totalLines));
                }
            }

            if (!batch.isEmpty()) {
                processBatch(batch, timestampFormat, result);
            }
        }

        progressCallback.accept(95, "Finalizing analysis");
        result.setTotalLines((int) totalLines);

        return result;
    }

    private void processBatch(List<String> lines, String timestampFormat,
                              LogAnalysisResult result) {
        for (String line : lines) {
            try {
                result.addLogEntry(parseLine(line, timestampFormat));
            } catch (Exception e) {
                log.warn("Failed to parse line: {}", line, e);
            }
        }
    }

    private LogEntry parseLine(String line, String timestampFormat) {
        // Implement your parsing logic
        return new LogEntry();
    }

    private void updateJobStatus(String jobId, JobStatusEnum status, int progress, String message) {
        JobStatus jobStatus = jobStatuses.get(jobId);
        if (jobStatus != null) {
            jobStatus.setStatus(status);
            jobStatus.setProgress(progress);
            jobStatus.setMessage(message);
            jobStatus.setUpdatedAt(LocalDateTime.now());
        }
    }

    public JobStatus getJobStatus(String jobId) {
        return jobStatuses.get(jobId);
    }

    public JobResult getJobResult(String jobId) {
        return jobResults.get(jobId);
    }
}
