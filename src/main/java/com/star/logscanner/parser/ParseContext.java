package com.star.logscanner.parser;

import com.star.logscanner.entity.LogEntry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds parsing context including configuration and mutable state
 * for processing log files. Passed to parsers to maintain state
 * across line parsing calls.
 * 
 * <p>This class supports:
 * <ul>
 *   <li>Timestamp format configuration</li>
 *   <li>Multi-line entry assembly (stack traces)</li>
 *   <li>Parser-specific custom configuration</li>
 *   <li>Processing statistics tracking</li>
 * </ul>
 * 
 * @author Eshmamatov Obidjon
 */
@Data
@Slf4j
public class ParseContext {

    private String timestampFormat;

    private DateTimeFormatter timestampFormatter;

    private String jobId;

    private String fileName;

    private boolean strictMode = false;

    private int maxLineLength = 100_000;

    private Map<String, Object> options = new HashMap<>();

    private LogEntry previousEntry;

    private StringBuilder multiLineBuffer = new StringBuilder();

    private long multiLineStartLine = -1;

    private boolean inMultiLine = false;

    private long totalLinesProcessed = 0;

    private long successfulLines = 0;

    private long failedLines = 0;

    private long skippedLines = 0;

    private long multiLineEntries = 0;

    private String[] csvHeaders;

    private Map<String, Integer> csvColumnMappings = new HashMap<>();

    private boolean csvHeadersProcessed = false;

    public ParseContext() {
        this(null);
    }

    public ParseContext(String timestampFormat) {
        this.timestampFormat = timestampFormat;
        if (timestampFormat != null && !timestampFormat.isEmpty()) {
            try {
                this.timestampFormatter = DateTimeFormatter.ofPattern(timestampFormat);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid timestamp format '{}', will use auto-detection", timestampFormat);
            }
        }
    }

    public ParseContext(String timestampFormat, String jobId, String fileName) {
        this(timestampFormat);
        this.jobId = jobId;
        this.fileName = fileName;
    }

    public void startMultiLine(long lineNumber, String content) {
        multiLineBuffer.setLength(0);
        multiLineBuffer.append(content);
        multiLineStartLine = lineNumber;
        inMultiLine = true;
    }

    public void appendToMultiLine(String content) {
        if (inMultiLine) {
            multiLineBuffer.append("\n").append(content);
        }
    }

    public String completeMultiLine() {
        String content = multiLineBuffer.toString();
        resetMultiLine();
        multiLineEntries++;
        return content;
    }

    public void resetMultiLine() {
        multiLineBuffer.setLength(0);
        multiLineStartLine = -1;
        inMultiLine = false;
    }

    public void recordSuccess() {
        totalLinesProcessed++;
        successfulLines++;
    }

    public void recordFailure() {
        totalLinesProcessed++;
        failedLines++;
    }

    public void recordSkipped() {
        totalLinesProcessed++;
        skippedLines++;
    }

    public double getSuccessRate() {
        if (totalLinesProcessed == 0) return 0;
        return (successfulLines * 100.0) / totalLinesProcessed;
    }

    public ParseContext withOption(String key, Object value) {
        options.put(key, value);
        return this;
    }
    

    @SuppressWarnings("unchecked")
    public <T> T getOption(String key, T defaultValue) {
        Object value = options.get(key);
        return value != null ? (T) value : defaultValue;
    }

    public boolean hasOption(String key) {
        return options.containsKey(key);
    }

    public void reset() {
        previousEntry = null;
        resetMultiLine();
        totalLinesProcessed = 0;
        successfulLines = 0;
        failedLines = 0;
        skippedLines = 0;
        multiLineEntries = 0;
        csvHeaders = null;
        csvColumnMappings.clear();
        csvHeadersProcessed = false;
    }

    public ParseContext copyWithFreshState() {
        ParseContext copy = new ParseContext(timestampFormat, jobId, fileName);
        copy.setStrictMode(strictMode);
        copy.setMaxLineLength(maxLineLength);
        copy.options.putAll(options);
        return copy;
    }

    public static class Builder {
        private String timestampFormat;
        private String jobId;
        private String fileName;
        private boolean strictMode = false;
        private int maxLineLength = 100_000;
        private Map<String, Object> options = new HashMap<>();
        
        public Builder timestampFormat(String format) {
            this.timestampFormat = format;
            return this;
        }
        
        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }
        
        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }
        
        public Builder strictMode(boolean strict) {
            this.strictMode = strict;
            return this;
        }
        
        public Builder maxLineLength(int maxLength) {
            this.maxLineLength = maxLength;
            return this;
        }
        
        public Builder option(String key, Object value) {
            this.options.put(key, value);
            return this;
        }
        
        public ParseContext build() {
            ParseContext ctx = new ParseContext(timestampFormat, jobId, fileName);
            ctx.setStrictMode(strictMode);
            ctx.setMaxLineLength(maxLineLength);
            ctx.getOptions().putAll(options);
            return ctx;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
