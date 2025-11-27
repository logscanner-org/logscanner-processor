package com.star.logscanner.exception;

import com.star.logscanner.dto.ApiResponse;
import com.star.logscanner.dto.ErrorResponse;
import com.star.logscanner.dto.ValidationError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(FileSizeLimitExceededException.class)
    public ResponseEntity<?> handleFileSizeLimitExceeded(
            FileSizeLimitExceededException ex,
            HttpServletRequest request) {

        log.error("File size limit exceeded: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                "FILE_SIZE_LIMIT_EXCEEDED",
                ex.getMessage(),
                request.getRequestURI(),
                HttpStatus.PAYLOAD_TOO_LARGE.value()
        );

        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("File size exceeds the allowed limit", errorResponse));
    }

    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<?> handleInvalidFileType(
            InvalidFileTypeException ex,
            HttpServletRequest request) {

        log.error("Invalid file type: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                "INVALID_FILE_TYPE",
                ex.getMessage(),
                request.getRequestURI(),
                HttpStatus.BAD_REQUEST.value()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid file type", errorResponse));
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<?> handleFileUpload(
            FileUploadException ex,
            HttpServletRequest request) {

        log.error("File upload error: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                "FILE_UPLOAD_ERROR",
                ex.getMessage(),
                request.getRequestURI(),
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to upload file", errorResponse));
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<?> handleJobNotFound(
            JobNotFoundException ex,
            HttpServletRequest request) {

        log.error("Job not found: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                "JOB_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                HttpStatus.NOT_FOUND.value()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Job not found", errorResponse));
    }

    @ExceptionHandler(LogProcessingException.class)
    public ResponseEntity<?> handleLogProcessing(
            LogProcessingException ex,
            HttpServletRequest request) {

        log.error("Log processing error: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                "LOG_PROCESSING_ERROR",
                ex.getMessage(),
                request.getRequestURI(),
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to process log file", errorResponse));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {

        log.error("Max upload size exceeded: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                "MAX_UPLOAD_SIZE_EXCEEDED",
                "File size exceeds the maximum allowed size for upload",
                request.getRequestURI(),
                HttpStatus.PAYLOAD_TOO_LARGE.value()
        );

        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("File too large", errorResponse));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<ValidationError> validationErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ValidationError(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());

        ErrorResponse errorResponse = new ErrorResponse(
                "VALIDATION_ERROR",
                "Validation failed for one or more fields",
                request.getRequestURI(),
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                validationErrors
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", errorResponse));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        log.error("Missing request parameter: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                "MISSING_PARAMETER",
                String.format("Required parameter '%s' is missing", ex.getParameterName()),
                request.getRequestURI(),
                HttpStatus.BAD_REQUEST.value()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Missing required parameter", errorResponse));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI(),
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error", errorResponse));
    }
}
