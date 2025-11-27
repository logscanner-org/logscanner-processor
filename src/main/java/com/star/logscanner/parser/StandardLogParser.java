package com.star.logscanner.parser;

import com.star.logscanner.entity.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for standard log formats (Log4j, Logback, etc.)
 * Handles formats like:
 * - 2024-01-15 10:30:45.123 [main] ERROR com.example.Class - Error message
 * - [2024-01-15 10:30:45] INFO: Application started
 * - 2024/01/15 10:30:45 WARN [Thread-1] Message here
 */
@Component
@Slf4j
public class StandardLogParser implements LogParser {
    
    // Common log patterns
    private static final Pattern STANDARD_PATTERN = Pattern.compile(
        "^(?<timestamp>[\\d\\-\\s:\\.T]+)\\s*" +
        "(?:\\[(?<thread>[^\\]]+)\\])?\\s*" +
        "(?<level>TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)?\\s*" +
        "(?:(?<logger>[\\w\\.]+)\\s*[-:])?\\s*" +
        "(?<message>.*?)$",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern SIMPLE_PATTERN = Pattern.compile(
        "^\\[?(?<timestamp>[^\\]]+)\\]?\\s+" +
        "(?<level>TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)\\s*:?\\s+" +
        "(?<message>.*?)$",
        Pattern.CASE_INSENSITIVE
    );
    
    // Stack trace pattern
    private static final Pattern STACK_TRACE_START = Pattern.compile(
        "^(?:Caused by:|\\s+at |\\s+\\.{3}\\s+\\d+ more)"
    );
    
    @Override
    public LogEntry parseLine(String line, long lineNumber, String timestampFormat) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        
        // Try standard pattern first
        Matcher matcher = STANDARD_PATTERN.matcher(line);
        if (matcher.matches()) {
            return parseWithMatcher(matcher, line, lineNumber, timestampFormat);
        }
        
        // Try simple pattern
        matcher = SIMPLE_PATTERN.matcher(line);
        if (matcher.matches()) {
            return parseWithMatcher(matcher, line, lineNumber, timestampFormat);
        }
        
        // If no pattern matches, create a basic entry
        return createBasicEntry(line, lineNumber);
    }
    
    private LogEntry parseWithMatcher(Matcher matcher, String line, long lineNumber, String timestampFormat) {
        LogEntry entry = LogEntry.builder()
                .id(UUID.randomUUID().toString())
                .lineNumber(lineNumber)
                .rawLine(line)
                .indexedAt(LocalDateTime.now())
                .build();
        
        // Parse timestamp
        String timestampStr = matcher.group("timestamp");
        if (timestampStr != null) {
            LocalDateTime timestamp = parseTimestamp(timestampStr, timestampFormat);
            entry.setTimestamp(timestamp);
        }
        
        // Parse level
        String level = matcher.group("level");
        if (level != null) {
            entry.setLevel(normalizeLogLevel(level.toUpperCase()));
            entry.setHasError("ERROR".equals(entry.getLevel()) || "FATAL".equals(entry.getLevel()));
        }
        
        // Parse thread
        try {
            String thread = matcher.group("thread");
            if (thread != null) {
                entry.setThread(thread);
            }
        } catch (IllegalArgumentException e) {
            // Group not found, ignore
        }
        
        // Parse logger
        try {
            String logger = matcher.group("logger");
            if (logger != null) {
                entry.setLogger(logger);
                
                // Extract source from logger (last part)
                String[] parts = logger.split("\\.");
                if (parts.length > 0) {
                    entry.setSource(parts[parts.length - 1]);
                }
            }
        } catch (IllegalArgumentException e) {
            // Group not found, ignore
        }
        
        // Parse message
        String message = matcher.group("message");
        if (message != null) {
            entry.setMessage(message.trim());
            
            // Check if message looks like the start of a stack trace
            if (STACK_TRACE_START.matcher(message).find()) {
                entry.setHasStackTrace(true);
                entry.setStackTrace(message);
            }
        }
        
        // Extract additional metadata
        entry.setMetadata(extractMetadata(line, matcher));
        
        return entry;
    }
    
    private LogEntry createBasicEntry(String line, long lineNumber) {
        LogEntry entry = LogEntry.builder()
                .id(UUID.randomUUID().toString())
                .lineNumber(lineNumber)
                .rawLine(line)
                .message(line)
                .indexedAt(LocalDateTime.now())
                .build();
        
        // Check if it's a stack trace continuation
        if (STACK_TRACE_START.matcher(line).find()) {
            entry.setHasStackTrace(true);
            entry.setStackTrace(line);
        }
        
        return entry;
    }
    
    private LocalDateTime parseTimestamp(String timestampStr, String format) {
        if (timestampStr == null || timestampStr.isEmpty()) {
            return LocalDateTime.now();
        }
        
        // Try provided format first
        if (format != null && !format.isEmpty()) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                return LocalDateTime.parse(timestampStr.trim(), formatter);
            } catch (DateTimeParseException e) {
                log.debug("Failed to parse timestamp with provided format: {}", format);
            }
        }
        
        // Try common formats
        String[] commonFormats = {
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "dd/MMM/yyyy:HH:mm:ss",
            "MMM dd, yyyy HH:mm:ss",
            "MMM dd HH:mm:ss",
            "ISO_LOCAL_DATE_TIME"
        };
        
        for (String commonFormat : commonFormats) {
            try {
                DateTimeFormatter formatter = "ISO_LOCAL_DATE_TIME".equals(commonFormat) 
                    ? DateTimeFormatter.ISO_LOCAL_DATE_TIME 
                    : DateTimeFormatter.ofPattern(commonFormat);
                return LocalDateTime.parse(timestampStr.trim(), formatter);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        
        log.debug("Could not parse timestamp: {}", timestampStr);
        return LocalDateTime.now();
    }
    
    private String normalizeLogLevel(String level) {
        if (level == null) {
            return "INFO";
        }
        
        // Normalize common variations
        switch (level.toUpperCase()) {
            case "WARNING":
            case "WARN":
                return "WARN";
            case "SEVERE":
            case "FATAL":
                return "ERROR";
            case "FINE":
            case "FINER":
            case "FINEST":
                return "DEBUG";
            case "CONFIG":
                return "INFO";
            default:
                return level.toUpperCase();
        }
    }
    
    private Map<String, Object> extractMetadata(String line, Matcher matcher) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Extract key-value pairs from the message
        Pattern kvPattern = Pattern.compile("([\\w]+)=([^\\s,]+)");
        Matcher kvMatcher = kvPattern.matcher(line);
        
        while (kvMatcher.find()) {
            String key = kvMatcher.group(1);
            String value = kvMatcher.group(2);
            metadata.put(key, value);
        }
        
        // Extract IP addresses
        Pattern ipPattern = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
        Matcher ipMatcher = ipPattern.matcher(line);
        if (ipMatcher.find()) {
            metadata.put("ip_address", ipMatcher.group());
        }
        
        // Extract URLs
        Pattern urlPattern = Pattern.compile("https?://[^\\s]+");
        Matcher urlMatcher = urlPattern.matcher(line);
        if (urlMatcher.find()) {
            metadata.put("url", urlMatcher.group());
        }
        
        // Extract request IDs or correlation IDs
        Pattern idPattern = Pattern.compile("(?:request[_-]?id|correlation[_-]?id|trace[_-]?id)[=:]\\s*([\\w-]+)", 
                                           Pattern.CASE_INSENSITIVE);
        Matcher idMatcher = idPattern.matcher(line);
        if (idMatcher.find()) {
            metadata.put("request_id", idMatcher.group(1));
        }
        
        return metadata.isEmpty() ? null : metadata;
    }
    
    @Override
    public boolean canParse(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        
        // Check if line matches any of our patterns
        return STANDARD_PATTERN.matcher(line).find() || 
               SIMPLE_PATTERN.matcher(line).find() ||
               STACK_TRACE_START.matcher(line).find();
    }
    
    @Override
    public String getParserType() {
        return "STANDARD";
    }
}
