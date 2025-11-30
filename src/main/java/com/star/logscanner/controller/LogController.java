package com.star.logscanner.controller;

import com.star.logscanner.dto.ApiResponse;
import com.star.logscanner.dto.JobStatusEnum;
import com.star.logscanner.dto.UploadResponse;
import com.star.logscanner.dto.query.*;
import com.star.logscanner.entity.JobResult;
import com.star.logscanner.entity.JobStatus;
import com.star.logscanner.exception.*;
import com.star.logscanner.service.LogProcessingService;
import com.star.logscanner.service.LogQueryService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/logs")
@Slf4j
public class LogController {

    @Autowired
    private LogProcessingService logProcessingService;

    @Autowired
    private LogQueryService logQueryService;

    @Value("${app.file.max-size:52428800}")
    private long maxFileSize;

    @Value("${app.file.allowed-types:log,txt}")
    private String[] allowedFileTypes;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadLogFile(
            @RequestParam("logfile") MultipartFile logfile,
            @RequestParam("timestampFormat") String timestampFormat) {

        log.info("Received file upload request: {} ({})",
                logfile.getOriginalFilename(),
                logfile.getSize());

        if (logfile.isEmpty()) {
            throw new FileUploadException("No file uploaded or file is empty");
        }

        if (logfile.getSize() > maxFileSize) {
            throw new FileSizeLimitExceededException(maxFileSize, logfile.getSize());
        }

        String originalFilename = logfile.getOriginalFilename();
        if (originalFilename == null || !isValidFileType(originalFilename)) {
            throw new InvalidFileTypeException(
                    "Invalid file type. Allowed types: " + String.join(", ", allowedFileTypes)
            );
        }

        try {
            String jobId = UUID.randomUUID().toString();
            Path temporaryFile = logProcessingService.saveTemporaryFile(logfile);
            logProcessingService.processLogFileAsync(jobId, logfile, temporaryFile, timestampFormat);

            UploadResponse uploadResponse = new UploadResponse(
                    jobId,
                    "/logs/status/" + jobId,
                    "/logs/result/" + jobId,
                    originalFilename,
                    logfile.getSize()
            );

            log.info("File upload accepted for processing. Job ID: {}", jobId);

            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success(
                            "File uploaded successfully and queued for processing",
                            uploadResponse
                    ));

        } catch (Exception e) {
            log.error("Error processing file upload", e);
            throw new FileUploadException("Failed to process file upload", e);
        }
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> getJobStatus(
            @PathVariable String jobId) {

        log.debug("Fetching status for job: {}", jobId);

        JobStatus status = logProcessingService.getJobStatus(jobId);

        if (status == null) {
            throw new JobNotFoundException(jobId);
        }

        return ResponseEntity.ok(
                ApiResponse.success("Job status retrieved successfully", status)
        );
    }

    @GetMapping("/result/{jobId}")
    public ResponseEntity<?> getJobResult(
            @PathVariable String jobId) {

        log.debug("Fetching result for job: {}", jobId);

        JobResult result = logProcessingService.getJobResult(jobId);

        if (result == null) {
            throw new JobNotFoundException(jobId);
        }

        JobStatus status = logProcessingService.getJobStatus(jobId);
        if (status != null && status.getStatus() != JobStatusEnum.COMPLETED) {
            throw new LogProcessingException(
                    "Job is not yet completed. Current status: " + status.getStatus()
            );
        }

        return ResponseEntity.ok(
                ApiResponse.success("Analysis result retrieved successfully", result.getAnalysisResult())
        );
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<LogQueryResponse>> searchLogs(
            @Valid @RequestBody LogQueryRequest request) {

        log.info("Search request for job: {}", request.getJobId());

        LogQueryResponse response = logQueryService.searchLogs(request);

        return ResponseEntity.ok(
                ApiResponse.success("Search completed successfully", response)
        );
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<LogQueryResponse>> searchLogsGet(
            @RequestParam String jobId,
            @RequestParam(required = false) String searchText,
            @RequestParam(required = false) List<String> levels,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String logger,
            @RequestParam(required = false) String thread,
            @RequestParam(required = false) Boolean hasError,
            @RequestParam(required = false) Boolean hasStackTrace,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {

        LogQueryRequest request = LogQueryRequest.builder()
                .jobId(jobId)
                .searchText(searchText)
                .levels(levels)
                .fileName(fileName)
                .logger(logger)
                .thread(thread)
                .hasError(hasError)
                .hasStackTrace(hasStackTrace)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .page(page)
                .size(size)
                .build();

        LogQueryResponse response = logQueryService.searchLogs(request);

        return ResponseEntity.ok(
                ApiResponse.success("Search completed successfully", response)
        );
    }

    @GetMapping("/job/{jobId}/summary")
    public ResponseEntity<ApiResponse<JobSummary>> getJobSummary(
            @PathVariable String jobId) {

        log.info("Getting summary for job: {}", jobId);

        JobSummary summary = logQueryService.getJobSummary(jobId);

        return ResponseEntity.ok(
                ApiResponse.success("Job summary retrieved successfully", summary)
        );
    }

    @GetMapping("/job/{jobId}/levels")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getLevelDistribution(
            @PathVariable String jobId) {

        log.debug("Getting level distribution for job: {}", jobId);

        Map<String, Long> distribution = logQueryService.getLevelDistribution(jobId);

        return ResponseEntity.ok(
                ApiResponse.success("Level distribution retrieved successfully", distribution)
        );
    }

    @GetMapping("/job/{jobId}/timeline")
    public ResponseEntity<ApiResponse<TimelineData>> getTimeline(
            @PathVariable String jobId,
            @Parameter(description = "Time interval: 1m, 5m, 15m, 30m, 1h, 1d, 1w, 1M")
            @RequestParam(defaultValue = "1h") String interval) {

        log.info("Getting timeline for job: {}, interval: {}", jobId, interval);

        TimelineData timeline = logQueryService.getTimelineData(jobId, interval);

        return ResponseEntity.ok(
                ApiResponse.success("Timeline data retrieved successfully", timeline)
        );
    }

    @GetMapping("/job/{jobId}/fields/{fieldName}")
    public ResponseEntity<ApiResponse<List<LogQueryResponse.FieldCount>>> getUniqueFieldValues(
            @PathVariable String jobId,
            @PathVariable String fieldName,
            @RequestParam(defaultValue = "100") int limit) {

        log.debug("Getting unique values for field '{}' in job: {}", fieldName, jobId);

        List<LogQueryResponse.FieldCount> values = logQueryService.getUniqueFieldValues(jobId, fieldName, limit);

        return ResponseEntity.ok(
                ApiResponse.success("Field values retrieved successfully", values)
        );
    }

    @GetMapping("/job/{jobId}/fields")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getAvailableFields(
            @PathVariable String jobId) {

        log.debug("Getting available fields for job: {}", jobId);

        // Return common fields with their unique values
        Map<String, List<String>> fields = Map.of(
                "levels", List.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE"),
                "loggers", logQueryService.getUniqueFieldValuesList(jobId, "logger"),
                "threads", logQueryService.getUniqueFieldValuesList(jobId, "thread"),
                "sources", logQueryService.getUniqueFieldValuesList(jobId, "source"),
                "hostnames", logQueryService.getUniqueFieldValuesList(jobId, "hostname"),
                "applications", logQueryService.getUniqueFieldValuesList(jobId, "application")
        );

        return ResponseEntity.ok(
                ApiResponse.success("Available fields retrieved successfully", fields)
        );
    }

    @PostMapping("/job/{jobId}/export")
    public ResponseEntity<byte[]> exportLogs(
            @PathVariable String jobId,
            @RequestBody(required = false) ExportRequest request,
            @RequestParam(defaultValue = "csv") String format) {

        log.info("Exporting logs for job: {}, format: {}", jobId, format);

        if (request == null) {
            request = ExportRequest.builder()
                    .format(ExportRequest.ExportFormat.valueOf(format.toUpperCase()))
                    .build();
        }

        byte[] data;
        String contentType;
        String filename;

        switch (request.getFormat()) {
            case JSON:
                data = logQueryService.exportToJson(jobId, request);
                contentType = MediaType.APPLICATION_JSON_VALUE;
                filename = "logs-" + jobId + ".json";
                break;
            case NDJSON:
                data = logQueryService.exportToNdjson(jobId, request);
                contentType = "application/x-ndjson";
                filename = "logs-" + jobId + ".ndjson";
                break;
            case CSV:
            default:
                data = logQueryService.exportToCsv(jobId, request);
                contentType = "text/csv";
                filename = "logs-" + jobId + ".csv";
                break;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(data);
    }

    @GetMapping("/job/{jobId}/export")
    public ResponseEntity<byte[]> exportLogsGet(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(defaultValue = "10000") Integer maxRecords) {

        ExportRequest request = ExportRequest.builder()
                .format(ExportRequest.ExportFormat.valueOf(format.toUpperCase()))
                .maxRecords(maxRecords)
                .build();

        return exportLogs(jobId, request, format);
    }

    @GetMapping("/job/{jobId}/context/{lineNumber}")
    public ResponseEntity<ApiResponse<LogQueryResponse>> getContextLines(
            @PathVariable String jobId,
            @PathVariable Long lineNumber,
            @RequestParam(defaultValue = "5") Integer before,
            @RequestParam(defaultValue = "5") Integer after) {

        log.debug("Getting context for line {} in job: {}", lineNumber, jobId);

        LogQueryRequest request = LogQueryRequest.builder()
                .jobId(jobId)
                .minLineNumber(Math.max(1, lineNumber - before))
                .maxLineNumber(lineNumber + after)
                .sortBy("lineNumber")
                .sortDirection("asc")
                .size(before + after + 1)
                .includeSummary(false)
                .build();

        LogQueryResponse response = logQueryService.searchLogs(request);

        return ResponseEntity.ok(
                ApiResponse.success("Context lines retrieved successfully", response)
        );
    }

    private boolean isValidFileType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return Arrays.asList(allowedFileTypes).contains(extension);
    }
}