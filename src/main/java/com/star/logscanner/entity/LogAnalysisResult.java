package com.star.logscanner.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class LogAnalysisResult {
    private int totalLines;
    private List<LogEntry> entries = new ArrayList<>();
    private Map<String, Integer> errorCounts = new HashMap<>();

    public void addLogEntry(LogEntry entry) {
        entries.add(entry);
        totalLines++;
    }
}
