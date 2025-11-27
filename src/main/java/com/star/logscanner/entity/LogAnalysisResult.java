package com.star.logscanner.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class LogAnalysisResult {
    private int totalLines;
    private int processedLines;
    private int successfulLines;
    private int failedLines;

    private long totalEntries;
    private long errorCount;

    private List<LogEntry> entries = new ArrayList<>();
    private Map<String, Integer> errorCounts = new HashMap<>();
    private Map<String, Long> levelCounts = new HashMap<>();

    private Map<String, Object> summary = new HashMap<>();

    public void addLogEntry(LogEntry entry) {
        entries.add(entry);

        // Update error counts
        if (entry != null && "ERROR".equals(entry.getLevel())) {
            String logger = entry.getLogger() != null ? entry.getLogger() : "unknown";
            errorCounts.merge(logger, 1, Integer::sum);
        }
    }
}
