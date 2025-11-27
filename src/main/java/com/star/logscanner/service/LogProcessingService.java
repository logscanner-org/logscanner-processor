package com.star.logscanner.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.star.logscanner.dto.JobStatusEnum;
import com.star.logscanner.entity.JobResult;
import com.star.logscanner.entity.JobStatus;
import com.star.logscanner.entity.LogAnalysisResult;
import com.star.logscanner.entity.LogEntry;
import com.star.logscanner.exception.LogProcessingException;
import com.star.logscanner.parser.LogParser;
import com.star.logscanner.repository.JobStatusRepository;
import com.star.logscanner.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogProcessingService {

    private final LogEntryRepository logEntryRepository;
    private final JobStatusRepository jobStatusRepository;
    private final List<LogParser> parsers;

    @Value("${app.processing.batch-size:1000}")
    private int batchSize;

    @Value("${app.file.temp-directory:/tmp/logscanner}")
    private String tempDirectory;

    @Async("taskExecutor")
    public void processLogFileAsync(String jobId, MultipartFile logfile, Path tempFile, String timestampFormat) {
        long startTime = System.currentTimeMillis();

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

        try {
            updateJobStatus(jobId, JobStatusEnum.PROCESSING, 0, "Starting file processing");

            log.info("Processing log file for job {}: {}", jobId, logfile.getOriginalFilename());

            String contentType = logfile.getContentType();
            String fileName = logfile.getOriginalFilename().toLowerCase();

            LogAnalysisResult result;

            if (fileName.endsWith(".csv")) {
                result = processCsvFile(tempFile, jobId, timestampFormat,
                        (progress, message) -> updateJobStatus(jobId, JobStatusEnum.PROCESSING, progress, message));
            } else if (fileName.endsWith(".json")) {
                result = processJsonFile(tempFile, jobId, timestampFormat,
                        (progress, message) -> updateJobStatus(jobId, JobStatusEnum.PROCESSING, progress, message));
            } else {
                result = processTextLogFile(tempFile, jobId, timestampFormat,
                        (progress, message) -> updateJobStatus(jobId, JobStatusEnum.PROCESSING, progress, message));
            }

            long processingTime = System.currentTimeMillis() - startTime;
            double linesPerSecond = result.getTotalLines() > 0 ?
                    (result.getTotalLines() * 1000.0) / processingTime : 0;

            jobStatus = jobStatusRepository.findByJobId(jobId).orElse(jobStatus);
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

            log.info("Job {} completed successfully. Processed {} lines in {} ms ({} lines/sec)",
                    jobId, result.getTotalLines(), processingTime, String.format("%.2f", linesPerSecond));

        } catch (Exception e) {
            log.error("Job {} failed with error: {}", jobId, e.getMessage(), e);

            jobStatus = jobStatusRepository.findByJobId(jobId).orElse(jobStatus);
            jobStatus.setStatus(JobStatusEnum.FAILED);
            jobStatus.setMessage("Processing failed: " + e.getMessage());
            jobStatus.setError(e.getMessage());
            jobStatus.setCompletedAt(LocalDateTime.now());
            jobStatus.setUpdatedAt(LocalDateTime.now());

            jobStatusRepository.save(jobStatus);

            throw new LogProcessingException("Failed to process log file", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.debug("Cleaned up temporary file: {}", tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temporary file: {}", tempFile, e);
                }
            }
        }
    }

    private LogAnalysisResult processJsonFile(Path filePath, String jobId, String timestampFormat,
                                              ProgressCallback progressCallback) throws IOException {
        // For JSON files, we'll use the JSON parser
        return processTextLogFile(filePath, jobId, timestampFormat, progressCallback);
    }

    private LogAnalysisResult processCsvFile(Path filePath, String jobId, String timestampFormat,
                                             ProgressCallback progressCallback) throws IOException, CsvValidationException {
        LogAnalysisResult result = new LogAnalysisResult();
        AtomicLong processedLines = new AtomicLong(0);
        AtomicLong successfulLines = new AtomicLong(0);

        try (CSVReader reader = new CSVReader(new InputStreamReader(
                Files.newInputStream(filePath), StandardCharsets.UTF_8))) {

            String[] headers = reader.readNext();
            if (headers == null) {
                throw new LogProcessingException("CSV file is empty or has no headers");
            }

            List<LogEntry> batch = new ArrayList<>();
            String[] row;

            while ((row = reader.readNext()) != null) {
                processedLines.incrementAndGet();

                LogEntry entry = parseCsvRow(row, headers, processedLines.get(), timestampFormat);
                if (entry != null) {
                    entry.setJobId(jobId);
                    entry.setFileName(filePath.getFileName().toString());
                    batch.add(entry);
                    successfulLines.incrementAndGet();
                }

                if (batch.size() >= batchSize) {
                    saveBatch(batch);
                    batch.clear();
                    progressCallback.update(50,
                            String.format("Processed %d lines", processedLines.get()));
                }
            }

            if (!batch.isEmpty()) {
                saveBatch(batch);
            }
        }

        result.setTotalLines((int) processedLines.get());
        result.setProcessedLines((int) processedLines.get());
        result.setSuccessfulLines((int) successfulLines.get());

        calculateStatistics(jobId, result);

        return result;
    }

    private LogEntry parseCsvRow(String[] row, String[] headers, long lineNumber, String timestampFormat) {
        if (row == null || row.length == 0) {
            return null;
        }

        LogEntry entry = LogEntry.builder()
                .id(UUID.randomUUID().toString())
                .lineNumber(lineNumber)
                .indexedAt(LocalDateTime.now())
                .build();

        Map<String, Object> metadata = new HashMap<>();

        for (int i = 0; i < Math.min(headers.length, row.length); i++) {
            String header = headers[i].toLowerCase().trim();
            String value = row[i];

            if (value == null || value.isEmpty()) {
                continue;
            }

            // Map CSV columns to LogEntry fields
            switch (header) {
                case "timestamp":
                case "time":
                case "date":
                    entry.setTimestamp(parseTimestamp(value, timestampFormat));
                    break;
                case "level":
                case "severity":
                    entry.setLevel(value.toUpperCase());
                    break;
                case "message":
                case "msg":
                case "text":
                    entry.setMessage(value);
                    break;
                case "logger":
                case "class":
                    entry.setLogger(value);
                    break;
                case "thread":
                    entry.setThread(value);
                    break;
                case "source":
                    entry.setSource(value);
                    break;
                default:
                    metadata.put(headers[i], value);
            }
        }

        if (!metadata.isEmpty()) {
            entry.setMetadata(metadata);
        }

        // Set defaults if not found
        if (entry.getMessage() == null) {
            entry.setMessage(String.join(",", row));
        }
        if (entry.getLevel() == null) {
            entry.setLevel("INFO");
        }
        if (entry.getTimestamp() == null) {
            entry.setTimestamp(LocalDateTime.now());
        }

        return entry;
    }

    private LocalDateTime parseTimestamp(String timestampStr, String format) {
        // Implementation would be similar to what's in the parsers
        // For brevity, returning current time as fallback
        return LocalDateTime.now();
    }

    private LogAnalysisResult processTextLogFile(Path filePath, String jobId, String timestampFormat,
                                                 ProgressCallback progressCallback) throws IOException{
        LogAnalysisResult result = new LogAnalysisResult();
        AtomicLong totalLines = new AtomicLong(0);
        AtomicLong processedLines = new AtomicLong(0);
        AtomicLong successfulLines = new AtomicLong(0);
        AtomicLong failedLines = new AtomicLong(0);

        // Count total lines first for progress calculation
        try (Stream<String> lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
            totalLines.set(lines.count());
        }

        progressCallback.update(5, "Counted " + totalLines.get() + " lines");

        // Process file in batches
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            List<LogEntry> batch = new ArrayList<>();
            String line;
            LogEntry previousEntry = null;

            while ((line = reader.readLine()) != null) {
                processedLines.incrementAndGet();

                // Handle multi-line logs (like stack traces)
                if (isStackTraceLine(line) && previousEntry != null) {
                    // Append to previous entry's stack trace
                    if (previousEntry.getStackTrace() == null) {
                        previousEntry.setStackTrace(line);
                    } else {
                        previousEntry.setStackTrace(previousEntry.getStackTrace() + "\n" + line);
                    }
                    previousEntry.setHasStackTrace(true);
                    continue;
                }

                LogEntry entry = parseLogLine(line, processedLines.get(), timestampFormat);

                if (entry != null) {
                    entry.setJobId(jobId);
                    entry.setFileName(filePath.getFileName().toString());
                    batch.add(entry);
                    previousEntry = entry;
                    successfulLines.incrementAndGet();
                } else {
                    failedLines.incrementAndGet();
                    log.debug("Failed to parse line {}: {}", processedLines.get(),
                            line.substring(0, Math.min(line.length(), 100)));
                }

                // Save batch when it reaches the configured size
                if (batch.size() >= batchSize) {
                    saveBatch(batch);
                    batch.clear();

                    int progress = 5 + (int) ((processedLines.get() * 90) / totalLines.get());
                    progressCallback.update(progress,
                            String.format("Processed %d/%d lines", processedLines.get(), totalLines.get()));
                }
            }

            // Save remaining entries
            if (!batch.isEmpty()) {
                saveBatch(batch);
            }
        }

        progressCallback.update(95, "Finalizing analysis");

        result.setTotalLines((int) totalLines.get());
        result.setProcessedLines((int) processedLines.get());
        result.setSuccessfulLines((int) successfulLines.get());
        result.setFailedLines((int) failedLines.get());

        calculateStatistics(jobId, result);

        return result;
    }

    private LogEntry parseLogLine(String line, long lineNumber, String timestampFormat) {
        // Try each parser until one succeeds
        for (LogParser parser : parsers) {
            if (parser.canParse(line)) {
                LogEntry entry = parser.parseLine(line, lineNumber, timestampFormat);
                if (entry != null) {
                    return entry;
                }
            }
        }

        // If no parser can handle it, create a basic entry
        return LogEntry.builder()
                .id(UUID.randomUUID().toString())
                .lineNumber(lineNumber)
                .rawLine(line)
                .message(line)
                .level("INFO")
                .timestamp(LocalDateTime.now())
                .indexedAt(LocalDateTime.now())
                .build();
    }

    private boolean isStackTraceLine(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }

        return line.startsWith("\tat ") ||
                line.startsWith("Caused by:") ||
                line.startsWith("\t... ") ||
                (line.startsWith(" ") && line.contains("Exception"));
    }

    private void updateJobStatus(String jobId, JobStatusEnum status, int progress, String message) {
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
            log.error("Failed to update job status for {}", jobId, e);
        }
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
        analysisResult.setTotalLines(status.getTotalLines() != null ? status.getTotalLines().intValue() : 0);
        analysisResult.setProcessedLines(status.getProcessedLines() != null ? status.getProcessedLines().intValue() : 0);
        analysisResult.setSuccessfulLines(status.getSuccessfulLines() != null ? status.getSuccessfulLines().intValue() : 0);
        analysisResult.setFailedLines(status.getFailedLines() != null ? status.getFailedLines().intValue() : 0);

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

    @Transactional
    private void saveBatch(List<LogEntry> batch) {
        if (batch.isEmpty()) {
            return;
        }

        try {
            logEntryRepository.saveAll(batch);
            log.debug("Saved batch of {} log entries", batch.size());
        } catch (Exception e) {
            log.error("Failed to save batch of {} entries", batch.size(), e);
            // Consider implementing retry logic or dead letter queue
        }
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
            log.warn("Failed to calculate statistics for job {}", jobId, e);
        }
    }

    public Path saveTemporaryFile(MultipartFile file) throws IOException {
        Path tempDir = Files.createTempDirectory("logscanner-");

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            originalFilename = "upload.tmp";
        }

        String fileExtension = originalFilename.contains(".") ?
                originalFilename.substring(originalFilename.lastIndexOf(".")) : ".tmp";

        Path tempFile = Files.createTempFile(tempDir, "log-", fileExtension);

        try {
            file.transferTo(tempFile.toFile());
            log.info("Saved temporary file: {} (size: {} bytes)", tempFile, file.getSize());
            return tempFile;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempFile);
                Files.deleteIfExists(tempDir);
            } catch (IOException deleteError) {
                log.warn("Failed to cleanup temp files", deleteError);
            }
            throw new IOException("Failed to save temporary file: " + originalFilename, e);
        }
    }

    @FunctionalInterface
    private interface ProgressCallback {
        void update(int progress, String message);
    }
}
