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
 * @author LogScanner Team
 * @version 2.0
 */
@Data
@Slf4j
public class ParseContext {
    
    // ========== Configuration ==========
    
    /**
     * Custom timestamp format pattern (optional).
     */
    private String timestampFormat;
    
    /**
     * Pre-compiled timestamp formatter for performance.
     */
    private DateTimeFormatter timestampFormatter;
    
    /**
     * Job ID for associating entries.
     */
    private String jobId;
    
    /**
     * Source filename.
     */
    private String fileName;
    
    /**
     * Enable strict parsing (fail on malformed lines).
     */
    private boolean strictMode = false;
    
    /**
     * Maximum line length to parse (prevents OOM on malformed files).
     */
    private int maxLineLength = 100_000;
    
    /**
     * Parser-specific configuration options.
     */
    private Map<String, Object> options = new HashMap<>();
    
    // ========== Multi-line State ==========
    
    /**
     * Previous entry for multi-line assembly.
     */
    private LogEntry previousEntry;
    
    /**
     * Buffer for multi-line log assembly.
     */
    private StringBuilder multiLineBuffer = new StringBuilder();
    
    /**
     * Starting line number of current multi-line entry.
     */
    private long multiLineStartLine = -1;
    
    /**
     * Flag indicating we're in a multi-line context.
     */
    private boolean inMultiLine = false;
    
    // ========== Statistics ==========
    
    /**
     * Total lines processed.
     */
    private long totalLinesProcessed = 0;
    
    /**
     * Successfully parsed lines.
     */
    private long successfulLines = 0;
    
    /**
     * Failed lines.
     */
    private long failedLines = 0;
    
    /**
     * Skipped lines.
     */
    private long skippedLines = 0;
    
    /**
     * Multi-line entries detected.
     */
    private long multiLineEntries = 0;
    
    // ========== CSV-Specific State ==========
    
    /**
     * CSV column headers (detected or configured).
     */
    private String[] csvHeaders;
    
    /**
     * Column index mappings for CSV parsing.
     */
    private Map<String, Integer> csvColumnMappings = new HashMap<>();
    
    /**
     * Whether CSV headers have been processed.
     */
    private boolean csvHeadersProcessed = false;
    
    // ========== Constructors ==========
    
    /**
     * Create context with default settings.
     */
    public ParseContext() {
        this(null);
    }
    
    /**
     * Create context with timestamp format.
     * 
     * @param timestampFormat custom timestamp format pattern
     */
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
    
    /**
     * Create context with full configuration.
     * 
     * @param timestampFormat custom timestamp format
     * @param jobId the job ID
     * @param fileName source filename
     */
    public ParseContext(String timestampFormat, String jobId, String fileName) {
        this(timestampFormat);
        this.jobId = jobId;
        this.fileName = fileName;
    }
    
    // ========== Multi-line Handling ==========
    
    /**
     * Start buffering a multi-line entry.
     * 
     * @param lineNumber starting line number
     * @param content initial content
     */
    public void startMultiLine(long lineNumber, String content) {
        multiLineBuffer.setLength(0);
        multiLineBuffer.append(content);
        multiLineStartLine = lineNumber;
        inMultiLine = true;
    }
    
    /**
     * Append content to the multi-line buffer.
     * 
     * @param content content to append
     */
    public void appendToMultiLine(String content) {
        if (inMultiLine) {
            multiLineBuffer.append("\n").append(content);
        }
    }
    
    /**
     * Complete the multi-line entry and return assembled content.
     * 
     * @return the complete multi-line content
     */
    public String completeMultiLine() {
        String content = multiLineBuffer.toString();
        resetMultiLine();
        multiLineEntries++;
        return content;
    }
    
    /**
     * Reset multi-line state without returning content.
     */
    public void resetMultiLine() {
        multiLineBuffer.setLength(0);
        multiLineStartLine = -1;
        inMultiLine = false;
    }
    
    /**
     * Check if currently buffering multi-line content.
     * 
     * @return true if in multi-line mode
     */
    public boolean isInMultiLine() {
        return inMultiLine;
    }
    
    /**
     * Get the starting line of current multi-line entry.
     * 
     * @return line number or -1 if not in multi-line mode
     */
    public long getMultiLineStartLine() {
        return multiLineStartLine;
    }
    
    // ========== Statistics ==========
    
    /**
     * Record a successful parse.
     */
    public void recordSuccess() {
        totalLinesProcessed++;
        successfulLines++;
    }
    
    /**
     * Record a failed parse.
     */
    public void recordFailure() {
        totalLinesProcessed++;
        failedLines++;
    }
    
    /**
     * Record a skipped line.
     */
    public void recordSkipped() {
        totalLinesProcessed++;
        skippedLines++;
    }
    
    /**
     * Get parsing success rate.
     * 
     * @return success rate as percentage (0-100)
     */
    public double getSuccessRate() {
        if (totalLinesProcessed == 0) return 0;
        return (successfulLines * 100.0) / totalLinesProcessed;
    }
    
    // ========== Options ==========
    
    /**
     * Set a parser-specific option.
     * 
     * @param key option key
     * @param value option value
     * @return this context for chaining
     */
    public ParseContext withOption(String key, Object value) {
        options.put(key, value);
        return this;
    }
    
    /**
     * Get a parser-specific option.
     * 
     * @param key option key
     * @param defaultValue default if not set
     * @param <T> option type
     * @return option value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getOption(String key, T defaultValue) {
        Object value = options.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    /**
     * Check if an option is set.
     * 
     * @param key option key
     * @return true if option exists
     */
    public boolean hasOption(String key) {
        return options.containsKey(key);
    }
    
    // ========== Reset ==========
    
    /**
     * Reset all state for processing a new file.
     * Preserves configuration but clears statistics and state.
     */
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
    
    /**
     * Create a copy of this context with preserved configuration
     * but fresh state.
     * 
     * @return new ParseContext with same configuration
     */
    public ParseContext copyWithFreshState() {
        ParseContext copy = new ParseContext(timestampFormat, jobId, fileName);
        copy.setStrictMode(strictMode);
        copy.setMaxLineLength(maxLineLength);
        copy.options.putAll(options);
        return copy;
    }
    
    // ========== Builder Pattern Support ==========
    
    /**
     * Builder for fluent context creation.
     */
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
    
    /**
     * Create a new builder.
     * 
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}
