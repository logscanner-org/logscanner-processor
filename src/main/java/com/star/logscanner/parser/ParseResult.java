package com.star.logscanner.parser;

import com.star.logscanner.entity.LogEntry;
import lombok.Builder;
import lombok.Getter;

/**
 * Encapsulates the result of parsing a log line.
 * Provides rich feedback about parsing success, failures, and special cases
 * like multi-line continuations.
 * 
 * <p>Design Pattern: Result/Either Pattern - represents success or failure
 * without throwing exceptions in the hot path.
 * 
 * @author LogScanner Team
 * @version 2.0
 */
@Getter
@Builder
public class ParseResult {
    
    /**
     * The status of the parse operation.
     */
    public enum Status {
        /** Successfully parsed into a complete LogEntry */
        SUCCESS,
        
        /** Line is a continuation of a previous multi-line entry */
        CONTINUATION,
        
        /** Line could not be parsed (malformed) */
        FAILED,
        
        /** Line was skipped (empty, comment, header, etc.) */
        SKIPPED,
        
        /** Line needs to be buffered for multi-line assembly */
        BUFFERED
    }
    
    /**
     * The parsing status.
     */
    private final Status status;
    
    /**
     * The parsed LogEntry (null if status is not SUCCESS).
     */
    private final LogEntry entry;
    
    /**
     * Error message if parsing failed.
     */
    private final String errorMessage;
    
    /**
     * The line number where parsing failed or was processed.
     */
    private final long lineNumber;
    
    /**
     * Raw line content (useful for debugging and error reporting).
     */
    private final String rawLine;
    
    /**
     * Additional context data (parser-specific).
     */
    private final String additionalInfo;
    
    // ========== Factory Methods ==========
    
    /**
     * Create a successful parse result.
     * 
     * @param entry the successfully parsed LogEntry
     * @return ParseResult with SUCCESS status
     */
    public static ParseResult success(LogEntry entry) {
        return ParseResult.builder()
                .status(Status.SUCCESS)
                .entry(entry)
                .lineNumber(entry != null ? entry.getLineNumber() : 0)
                .rawLine(entry != null ? entry.getRawLine() : null)
                .build();
    }
    
    /**
     * Create a failed parse result.
     * 
     * @param lineNumber the line number that failed
     * @param rawLine the raw line content
     * @param errorMessage description of the failure
     * @return ParseResult with FAILED status
     */
    public static ParseResult failed(long lineNumber, String rawLine, String errorMessage) {
        return ParseResult.builder()
                .status(Status.FAILED)
                .lineNumber(lineNumber)
                .rawLine(rawLine)
                .errorMessage(errorMessage)
                .build();
    }
    
    /**
     * Create a continuation result for multi-line entries.
     * The line should be appended to the previous entry.
     * 
     * @param lineNumber the line number
     * @param rawLine the continuation content
     * @return ParseResult with CONTINUATION status
     */
    public static ParseResult continuation(long lineNumber, String rawLine) {
        return ParseResult.builder()
                .status(Status.CONTINUATION)
                .lineNumber(lineNumber)
                .rawLine(rawLine)
                .build();
    }
    
    /**
     * Create a skipped result for lines that should be ignored.
     * 
     * @param lineNumber the line number
     * @param reason reason for skipping
     * @return ParseResult with SKIPPED status
     */
    public static ParseResult skipped(long lineNumber, String reason) {
        return ParseResult.builder()
                .status(Status.SKIPPED)
                .lineNumber(lineNumber)
                .additionalInfo(reason)
                .build();
    }
    
    /**
     * Create a buffered result for lines that start a multi-line entry.
     * 
     * @param lineNumber the line number
     * @param rawLine the line content to buffer
     * @return ParseResult with BUFFERED status
     */
    public static ParseResult buffered(long lineNumber, String rawLine) {
        return ParseResult.builder()
                .status(Status.BUFFERED)
                .lineNumber(lineNumber)
                .rawLine(rawLine)
                .build();
    }
    
    // ========== Convenience Methods ==========
    
    /**
     * Check if parsing was successful.
     * 
     * @return true if status is SUCCESS
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    /**
     * Check if parsing failed.
     * 
     * @return true if status is FAILED
     */
    public boolean isFailed() {
        return status == Status.FAILED;
    }
    
    /**
     * Check if line is a continuation of a multi-line entry.
     * 
     * @return true if status is CONTINUATION
     */
    public boolean isContinuation() {
        return status == Status.CONTINUATION;
    }
    
    /**
     * Check if line was skipped.
     * 
     * @return true if status is SKIPPED
     */
    public boolean isSkipped() {
        return status == Status.SKIPPED;
    }
    
    /**
     * Check if line was buffered for multi-line assembly.
     * 
     * @return true if status is BUFFERED
     */
    public boolean isBuffered() {
        return status == Status.BUFFERED;
    }
    
    /**
     * Check if this result should increment the successful line counter.
     * 
     * @return true if this represents a countable success
     */
    public boolean shouldCountAsSuccess() {
        return status == Status.SUCCESS;
    }
    
    /**
     * Check if this result should increment the failed line counter.
     * 
     * @return true if this represents a countable failure
     */
    public boolean shouldCountAsFailure() {
        return status == Status.FAILED;
    }
    
    @Override
    public String toString() {
        return String.format("ParseResult{status=%s, line=%d, error='%s'}", 
                status, lineNumber, errorMessage != null ? errorMessage : "none");
    }
}
