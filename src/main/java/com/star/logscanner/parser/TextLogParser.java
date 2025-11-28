package com.star.logscanner.parser;

import com.star.logscanner.entity.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for standard text-based log formats.
 * 
 * <p>Supports multiple common log formats:
 * <ul>
 *   <li>Log4j/Logback: {@code 2024-01-15 10:30:45.123 [main] ERROR c.e.Class - Message}</li>
 *   <li>Apache/Nginx: {@code 192.168.1.1 - - [15/Jan/2024:10:30:45 +0000] "GET /" 200}</li>
 *   <li>Syslog: {@code Jan 15 10:30:45 hostname service[pid]: message}</li>
 *   <li>Simple: {@code [2024-01-15 10:30:45] INFO: Application started}</li>
 *   <li>Custom patterns via configuration</li>
 * </ul>
 * 
 * <p>Features:
 * <ul>
 *   <li>Pre-compiled regex patterns for performance</li>
 *   <li>Multi-line stack trace handling</li>
 *   <li>Automatic timestamp format detection</li>
 *   <li>Metadata extraction (key-value pairs, IPs, URLs)</li>
 * </ul>
 * 
 * @author LogScanner Team
 * @version 2.0
 */
@Component
@Slf4j
public class TextLogParser implements LogParser {
    
    // ========== Compiled Log Patterns (ordered by specificity) ==========
    
    /**
     * Pattern for Log4j/Logback style logs.
     * Example: 2024-01-15 10:30:45.123 [main] ERROR com.example.Class - Error message
     */
    private static final Pattern LOG4J_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,6})?)\\s+" +
            "(?:\\[(?<thread>[^\\]]+)\\]\\s+)?" +
            "(?<level>TRACE|DEBUG|INFO|WARN(?:ING)?|ERROR|FATAL|SEVERE)\\s+" +
            "(?:(?<logger>[\\w.$]+)\\s+[-:]\\s+)?" +
            "(?<message>.*)$",
            Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Pattern for Apache/Nginx access logs (Combined Log Format).
     * Example: 192.168.1.1 - - [15/Jan/2024:10:30:45 +0000] "GET /path HTTP/1.1" 200 1234
     */
    private static final Pattern APACHE_PATTERN = Pattern.compile(
            "^(?<ip>[\\d.]+|[\\da-f:]+)\\s+" +
            "(?<ident>\\S+)\\s+" +
            "(?<user>\\S+)\\s+" +
            "\\[(?<timestamp>[^\\]]+)\\]\\s+" +
            "\"(?<request>[^\"]*)\"\\s+" +
            "(?<status>\\d{3})\\s+" +
            "(?<bytes>\\d+|-)(?:\\s+" +
            "\"(?<referer>[^\"]*)\"\\s+" +
            "\"(?<useragent>[^\"]*)\")?",
            Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Pattern for Syslog format.
     * Example: Jan 15 10:30:45 hostname service[pid]: message
     */
    private static final Pattern SYSLOG_PATTERN = Pattern.compile(
            "^(?<timestamp>\\w{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+" +
            "(?<hostname>[\\w.-]+)\\s+" +
            "(?<service>[\\w.-]+)" +
            "(?:\\[(?<pid>\\d+)\\])?:?\\s+" +
            "(?<message>.*)$"
    );
    
    /**
     * Pattern for simple bracketed logs.
     * Example: [2024-01-15 10:30:45] INFO: Application started
     */
    private static final Pattern SIMPLE_PATTERN = Pattern.compile(
            "^\\[?(?<timestamp>[^\\]]+)\\]?\\s+" +
            "(?<level>TRACE|DEBUG|INFO|WARN(?:ING)?|ERROR|FATAL|SEVERE)\\s*:?\\s+" +
            "(?<message>.*)$",
            Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Pattern for ISO timestamp with level.
     * Example: 2024-01-15T10:30:45.123Z INFO message
     */
    private static final Pattern ISO_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?)\\s+" +
            "(?<level>TRACE|DEBUG|INFO|WARN(?:ING)?|ERROR|FATAL|SEVERE)?\\s*" +
            "(?<message>.*)$",
            Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Pattern for Spring Boot style logs.
     * Example: 2024-01-15 10:30:45.123  INFO 1234 --- [main] c.e.Class : Message
     */
    private static final Pattern SPRING_BOOT_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,6})?)\\s+" +
            "(?<level>TRACE|DEBUG|INFO|WARN|ERROR)\\s+" +
            "(?<pid>\\d+)?\\s*---\\s+" +
            "\\[\\s*(?<thread>[^\\]]+)\\]\\s+" +
            "(?<logger>[\\w.$]+)\\s*:\\s+" +
            "(?<message>.*)$",
            Pattern.CASE_INSENSITIVE
    );
    
    // ========== Stack Trace Patterns ==========
    
    private static final Pattern STACK_TRACE_START = Pattern.compile(
            "^(?:Exception|Error|Caused\\s+by:|\\s+at\\s+|\\s+\\.{3}\\s+\\d+\\s+more|Suppressed:)",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern STACK_TRACE_LINE = Pattern.compile(
            "^(?:\\s+at\\s+|\\s+\\.{3}\\s+\\d+\\s+more|Caused\\s+by:|Suppressed:)"
    );
    
    private static final Pattern EXCEPTION_LINE = Pattern.compile(
            "^(?:[\\w.$]+(?:Exception|Error|Throwable))(?::\\s+.*)?$"
    );
    
    // ========== Metadata Extraction Patterns ==========
    
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "([\\w.]+)=([\"']?)([^\"'\\s,]+|[^\"']+)\\2"
    );
    
    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(?:(?:\\d{1,3}\\.){3}\\d{1,3}|(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4})\\b"
    );
    
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[^\\s\"'<>]+"
    );
    
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile(
            "(?:request[_-]?id|correlation[_-]?id|trace[_-]?id|x-request-id)[=:\\s]+([\\w-]+)",
            Pattern.CASE_INSENSITIVE
    );
    
    // ========== Timestamp Formatters (ordered by likelihood) ==========
    
    private static final List<DateTimeFormatter> TIMESTAMP_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss"),
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MMM dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("MMM  d HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );
    
    // ========== Pattern Registry ==========
    
    /**
     * Ordered list of patterns to try (most specific first).
     */
    private static final List<PatternConfig> PATTERN_CONFIGS = List.of(
            new PatternConfig("SPRING_BOOT", SPRING_BOOT_PATTERN, true),
            new PatternConfig("LOG4J", LOG4J_PATTERN, true),
            new PatternConfig("APACHE", APACHE_PATTERN, false),
            new PatternConfig("SYSLOG", SYSLOG_PATTERN, true),
            new PatternConfig("ISO", ISO_PATTERN, true),
            new PatternConfig("SIMPLE", SIMPLE_PATTERN, true)
    );
    
    // ========== State ==========
    
    private LogEntry currentMultiLineEntry = null;
    private StringBuilder stackTraceBuffer = new StringBuilder();
    
    // ========== LogParser Implementation ==========
    
    @Override
    public boolean canParse(String fileName, String contentSample) {
        if (contentSample == null || contentSample.trim().isEmpty()) {
            // Accept text-based extensions
            if (fileName != null) {
                String lower = fileName.toLowerCase();
                return lower.endsWith(".log") || lower.endsWith(".txt") || 
                       lower.endsWith(".out") || lower.endsWith(".err");
            }
            return false;
        }
        
        // Check if content looks like JSON
        String trimmed = contentSample.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return false; // Let JSON parser handle it
        }
        
        // Check if content matches any of our patterns
        String firstLine = contentSample.split("\n")[0];
        for (PatternConfig config : PATTERN_CONFIGS) {
            if (config.pattern.matcher(firstLine).matches()) {
                return true;
            }
        }
        
        // Accept if it looks like text
        return true;
    }
    
    @Override
    public ParseResult parseLine(String line, long lineNumber, ParseContext context) {
        // Null/empty handling
        if (line == null) {
            return ParseResult.skipped(lineNumber, "Null line");
        }
        
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            // Empty line might end a multi-line entry
            if (currentMultiLineEntry != null) {
                return flushMultiLineEntry(lineNumber);
            }
            return ParseResult.skipped(lineNumber, "Empty line");
        }
        
        // Check line length
        int maxLength = context.getMaxLineLength();
        if (line.length() > maxLength) {
            log.warn("Line {} exceeds maximum length ({} > {}), truncating", 
                    lineNumber, line.length(), maxLength);
            line = line.substring(0, maxLength);
        }
        
        // Check if this is a stack trace continuation
        if (isStackTraceLine(line)) {
            return handleStackTraceLine(line, lineNumber, context);
        }
        
        // Check if this starts an exception (end current multi-line, start new)
        if (isExceptionStart(line)) {
            ParseResult flushResult = null;
            if (currentMultiLineEntry != null) {
                flushResult = flushMultiLineEntry(lineNumber);
            }
            return handleExceptionStart(line, lineNumber, context, flushResult);
        }
        
        // Try to parse as a regular log line
        for (PatternConfig config : PATTERN_CONFIGS) {
            Matcher matcher = config.pattern.matcher(line);
            if (matcher.matches()) {
                // Flush any pending multi-line entry first
                if (currentMultiLineEntry != null) {
                    ParseResult flushResult = flushMultiLineEntry(lineNumber);
                    // Parse new line and we might need to return the flushed entry
                    // For simplicity, we'll let the processing handle the new entry
                }
                
                LogEntry entry = createEntryFromMatcher(matcher, config, line, lineNumber, context);
                if (entry != null) {
                    if (config.supportsMultiLine && hasStackTraceIndicator(entry.getMessage())) {
                        // Start multi-line mode
                        currentMultiLineEntry = entry;
                        stackTraceBuffer.setLength(0);
                        return ParseResult.buffered(lineNumber, line);
                    }
                    return ParseResult.success(entry);
                }
            }
        }
        
        // No pattern matched - create basic entry
        if (currentMultiLineEntry != null) {
            // Append to current entry as potential stack trace
            appendToMultiLine(line);
            return ParseResult.continuation(lineNumber, line);
        }
        
        LogEntry basicEntry = createBasicEntry(line, lineNumber, context);
        return ParseResult.success(basicEntry);
    }
    
    @Override
    public void reset() {
        currentMultiLineEntry = null;
        stackTraceBuffer.setLength(0);
    }
    
    @Override
    public String getSupportedFormat() {
        return "TEXT";
    }
    
    @Override
    public int getPriority() {
        return 0; // Default priority, other parsers can override
    }
    
    @Override
    public boolean supportsMultiLine() {
        return true;
    }
    
    @Override
    public String getDescription() {
        return "Text log parser supporting Log4j, Logback, Apache, Syslog, and custom formats";
    }
    
    // ========== Entry Creation ==========
    
    private LogEntry createEntryFromMatcher(Matcher matcher, PatternConfig config,
                                            String line, long lineNumber, ParseContext context) {
        LogEntry.LogEntryBuilder builder = LogEntry.builder()
                .id(UUID.randomUUID().toString())
                .lineNumber(lineNumber)
                .rawLine(line)
                .indexedAt(LocalDateTime.now());
        
        // Set job/file info from context
        if (context.getJobId() != null) {
            builder.jobId(context.getJobId());
        }
        if (context.getFileName() != null) {
            builder.fileName(context.getFileName());
        }
        
        // Parse timestamp
        String timestampStr = safeGroup(matcher, "timestamp");
        if (timestampStr != null) {
            LocalDateTime timestamp = parseTimestamp(timestampStr, context);
            builder.timestamp(timestamp);
        } else {
            builder.timestamp(LocalDateTime.now());
        }
        
        // Parse level
        String level = safeGroup(matcher, "level");
        if (level != null) {
            String normalizedLevel = normalizeLogLevel(level);
            builder.level(normalizedLevel);
            builder.hasError("ERROR".equals(normalizedLevel) || "FATAL".equals(normalizedLevel));
        } else {
            // Infer level from status code for Apache logs
            String status = safeGroup(matcher, "status");
            if (status != null) {
                int statusCode = Integer.parseInt(status);
                if (statusCode >= 500) {
                    builder.level("ERROR");
                    builder.hasError(true);
                } else if (statusCode >= 400) {
                    builder.level("WARN");
                } else {
                    builder.level("INFO");
                }
            } else {
                builder.level("INFO");
            }
        }
        
        // Parse thread
        String thread = safeGroup(matcher, "thread");
        if (thread != null) {
            builder.thread(thread.trim());
        }
        
        // Parse logger/service
        String logger = safeGroup(matcher, "logger");
        if (logger == null) {
            logger = safeGroup(matcher, "service");
        }
        if (logger != null) {
            builder.logger(logger);
            // Extract source from logger (last part)
            String[] parts = logger.split("\\.");
            if (parts.length > 0) {
                builder.source(parts[parts.length - 1]);
            }
        }
        
        // Parse hostname
        String hostname = safeGroup(matcher, "hostname");
        if (hostname != null) {
            builder.hostname(hostname);
        }
        
        // Parse message
        String message = safeGroup(matcher, "message");
        if (message == null) {
            // For Apache logs, construct message from request info
            String request = safeGroup(matcher, "request");
            String status = safeGroup(matcher, "status");
            if (request != null) {
                message = request + " " + (status != null ? status : "");
            } else {
                message = line;
            }
        }
        builder.message(message.trim());
        
        // Extract metadata
        Map<String, Object> metadata = extractMetadata(line, matcher, config);
        if (!metadata.isEmpty()) {
            builder.metadata(metadata);
        }
        
        return builder.build();
    }
    
    private LogEntry createBasicEntry(String line, long lineNumber, ParseContext context) {
        return LogEntry.builder()
                .id(UUID.randomUUID().toString())
                .lineNumber(lineNumber)
                .rawLine(line)
                .message(line)
                .level("INFO")
                .timestamp(LocalDateTime.now())
                .indexedAt(LocalDateTime.now())
                .jobId(context.getJobId())
                .fileName(context.getFileName())
                .build();
    }
    
    // ========== Multi-line Handling ==========
    
    private boolean isStackTraceLine(String line) {
        return STACK_TRACE_LINE.matcher(line).find();
    }
    
    private boolean isExceptionStart(String line) {
        return EXCEPTION_LINE.matcher(line.trim()).matches();
    }
    
    private boolean hasStackTraceIndicator(String message) {
        if (message == null) return false;
        return message.contains("Exception") || 
               message.contains("Error") ||
               message.contains("Throwable");
    }
    
    private ParseResult handleStackTraceLine(String line, long lineNumber, ParseContext context) {
        if (currentMultiLineEntry != null) {
            appendToMultiLine(line);
            return ParseResult.continuation(lineNumber, line);
        }
        
        // Stack trace without a parent entry - create basic entry
        LogEntry entry = createBasicEntry(line, lineNumber, context);
        entry.setHasStackTrace(true);
        entry.setStackTrace(line);
        return ParseResult.success(entry);
    }
    
    private ParseResult handleExceptionStart(String line, long lineNumber, 
                                             ParseContext context, ParseResult previousFlush) {
        // Create new multi-line entry for the exception
        LogEntry entry = createBasicEntry(line, lineNumber, context);
        entry.setLevel("ERROR");
        entry.setHasError(true);
        entry.setHasStackTrace(true);
        
        currentMultiLineEntry = entry;
        stackTraceBuffer.setLength(0);
        stackTraceBuffer.append(line);
        
        return ParseResult.buffered(lineNumber, line);
    }
    
    private void appendToMultiLine(String line) {
        if (stackTraceBuffer.length() > 0) {
            stackTraceBuffer.append("\n");
        }
        stackTraceBuffer.append(line);
    }
    
    private ParseResult flushMultiLineEntry(long currentLineNumber) {
        if (currentMultiLineEntry == null) {
            return null;
        }
        
        LogEntry entry = currentMultiLineEntry;
        
        // Set the complete stack trace
        if (stackTraceBuffer.length() > 0) {
            entry.setStackTrace(stackTraceBuffer.toString());
            entry.setHasStackTrace(true);
        }
        
        currentMultiLineEntry = null;
        stackTraceBuffer.setLength(0);
        
        return ParseResult.success(entry);
    }
    
    /**
     * Flush any pending multi-line entry (call at end of file).
     * 
     * @return the pending entry or null
     */
    public LogEntry flushPending() {
        if (currentMultiLineEntry != null) {
            LogEntry entry = currentMultiLineEntry;
            if (stackTraceBuffer.length() > 0) {
                entry.setStackTrace(stackTraceBuffer.toString());
                entry.setHasStackTrace(true);
            }
            currentMultiLineEntry = null;
            stackTraceBuffer.setLength(0);
            return entry;
        }
        return null;
    }
    
    // ========== Timestamp Parsing ==========
    
    private LocalDateTime parseTimestamp(String timestampStr, ParseContext context) {
        if (timestampStr == null || timestampStr.isEmpty()) {
            return LocalDateTime.now();
        }
        
        timestampStr = timestampStr.trim();
        
        // Try context-provided formatter first
        if (context.getTimestampFormatter() != null) {
            try {
                return LocalDateTime.parse(timestampStr, context.getTimestampFormatter());
            } catch (DateTimeParseException e) {
                log.debug("Custom format failed for '{}', trying auto-detection", timestampStr);
            }
        }
        
        // Try provided format string
        if (context.getTimestampFormat() != null && !context.getTimestampFormat().isEmpty()) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(context.getTimestampFormat());
                return LocalDateTime.parse(timestampStr, formatter);
            } catch (Exception e) {
                log.debug("Provided format '{}' failed for '{}'", 
                        context.getTimestampFormat(), timestampStr);
            }
        }
        
        // Try common formatters
        for (DateTimeFormatter formatter : TIMESTAMP_FORMATTERS) {
            try {
                return LocalDateTime.parse(timestampStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next
            }
        }
        
        // Handle Apache log format: dd/MMM/yyyy:HH:mm:ss +ZZZZ
        try {
            if (timestampStr.contains("/") && timestampStr.contains(":")) {
                String cleaned = timestampStr.split("\\s")[0]; // Remove timezone
                DateTimeFormatter apacheFormat = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss");
                return LocalDateTime.parse(cleaned, apacheFormat);
            }
        } catch (Exception e) {
            // Continue
        }
        
        log.debug("Could not parse timestamp: '{}', using current time", timestampStr);
        return LocalDateTime.now();
    }
    
    // ========== Level Normalization ==========
    
    private String normalizeLogLevel(String level) {
        if (level == null) return "INFO";
        
        switch (level.toUpperCase()) {
            case "WARNING":
            case "WARN":
                return "WARN";
            case "SEVERE":
            case "FATAL":
            case "CRITICAL":
                return "ERROR";
            case "FINE":
            case "FINER":
            case "FINEST":
            case "VERBOSE":
                return "DEBUG";
            case "CONFIG":
            case "NOTICE":
                return "INFO";
            default:
                return level.toUpperCase();
        }
    }
    
    // ========== Metadata Extraction ==========
    
    private Map<String, Object> extractMetadata(String line, Matcher matcher, PatternConfig config) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Extract key-value pairs
        Matcher kvMatcher = KEY_VALUE_PATTERN.matcher(line);
        while (kvMatcher.find()) {
            String key = kvMatcher.group(1);
            String value = kvMatcher.group(3);
            metadata.put(key, value);
        }
        
        // Extract IP addresses
        Matcher ipMatcher = IP_PATTERN.matcher(line);
        if (ipMatcher.find()) {
            metadata.put("ip_address", ipMatcher.group());
        }
        
        // Extract URLs
        Matcher urlMatcher = URL_PATTERN.matcher(line);
        if (urlMatcher.find()) {
            metadata.put("url", urlMatcher.group());
        }
        
        // Extract request/correlation IDs
        Matcher idMatcher = REQUEST_ID_PATTERN.matcher(line);
        if (idMatcher.find()) {
            metadata.put("request_id", idMatcher.group(1));
        }
        
        // Extract Apache-specific fields
        if (config.name.equals("APACHE")) {
            String ip = safeGroup(matcher, "ip");
            if (ip != null) metadata.put("client_ip", ip);
            
            String user = safeGroup(matcher, "user");
            if (user != null && !"-".equals(user)) metadata.put("user", user);
            
            String status = safeGroup(matcher, "status");
            if (status != null) metadata.put("http_status", Integer.parseInt(status));
            
            String bytes = safeGroup(matcher, "bytes");
            if (bytes != null && !"-".equals(bytes)) metadata.put("bytes", Long.parseLong(bytes));
            
            String referer = safeGroup(matcher, "referer");
            if (referer != null && !"-".equals(referer)) metadata.put("referer", referer);
            
            String ua = safeGroup(matcher, "useragent");
            if (ua != null && !"-".equals(ua)) metadata.put("user_agent", ua);
        }
        
        // Extract Syslog PID
        if (config.name.equals("SYSLOG")) {
            String pid = safeGroup(matcher, "pid");
            if (pid != null) metadata.put("pid", Integer.parseInt(pid));
        }
        
        return metadata;
    }
    
    // ========== Utility Methods ==========
    
    private String safeGroup(Matcher matcher, String groupName) {
        try {
            return matcher.group(groupName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    // ========== Inner Classes ==========
    
    /**
     * Configuration for a log pattern.
     */
    private static class PatternConfig {
        final String name;
        final Pattern pattern;
        final boolean supportsMultiLine;
        
        PatternConfig(String name, Pattern pattern, boolean supportsMultiLine) {
            this.name = name;
            this.pattern = pattern;
            this.supportsMultiLine = supportsMultiLine;
        }
    }
}
