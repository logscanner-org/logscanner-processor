package com.star.logscanner.entity;

import lombok.Data;

@Data
public class LogEntry {
    private String timestamp;
    private String level;
    private String message;
    // Add other fields as needed
}
