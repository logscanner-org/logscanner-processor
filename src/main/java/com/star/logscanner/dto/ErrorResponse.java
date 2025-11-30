package com.star.logscanner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {
    private String error;
    private String message;
    private String path;
    private int status;
    private LocalDateTime timestamp;
    private List<ValidationError> validationErrors;
    private List<ValidationError> details;

    public ErrorResponse(String error, String message, String path, int status) {
        this.error = error;
        this.message = message;
        this.path = path;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(String error, String message, String path, int status,
                         LocalDateTime timestamp, List<ValidationError> validationErrors) {
        this.error = error;
        this.message = message;
        this.path = path;
        this.status = status;
        this.timestamp = timestamp;
        this.validationErrors = validationErrors;
    }
}