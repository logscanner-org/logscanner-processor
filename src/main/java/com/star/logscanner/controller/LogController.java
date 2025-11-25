package com.star.logscanner.controller;

import com.star.logscanner.dto.ApiResponse;
import com.star.logscanner.dto.JobStatusEnum;
import com.star.logscanner.dto.UploadResponse;
import com.star.logscanner.entity.JobResult;
import com.star.logscanner.entity.JobStatus;
import com.star.logscanner.exception.*;
import com.star.logscanner.service.LogProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/logs")
@Slf4j
public class LogController {

    @Autowired
    private LogProcessingService logProcessingService;

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

            logProcessingService.processLogFileAsync(jobId, logfile, timestampFormat);

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

        // Check if job is completed
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

    private boolean isValidFileType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return Arrays.asList(allowedFileTypes).contains(extension);
    }
}