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
 * @author Eshmamatov Obidjon
 */
@Getter
@Builder
public class ParseResult {
    
    public enum Status {
        SUCCESS,
        CONTINUATION,
        FAILED,
        SKIPPED,
        BUFFERED
    }

    private final Status status;
    private final LogEntry entry;
    private final String errorMessage;
    private final long lineNumber;
    private final String rawLine;
    private final String additionalInfo;

    public static ParseResult success(LogEntry entry) {
        return ParseResult.builder()
                .status(Status.SUCCESS)
                .entry(entry)
                .lineNumber(entry != null ? entry.getLineNumber() : 0)
                .rawLine(entry != null ? entry.getRawLine() : null)
                .build();
    }

    public static ParseResult failed(long lineNumber, String rawLine, String errorMessage) {
        return ParseResult.builder()
                .status(Status.FAILED)
                .lineNumber(lineNumber)
                .rawLine(rawLine)
                .errorMessage(errorMessage)
                .build();
    }

    public static ParseResult continuation(long lineNumber, String rawLine) {
        return ParseResult.builder()
                .status(Status.CONTINUATION)
                .lineNumber(lineNumber)
                .rawLine(rawLine)
                .build();
    }

    public static ParseResult skipped(long lineNumber, String reason) {
        return ParseResult.builder()
                .status(Status.SKIPPED)
                .lineNumber(lineNumber)
                .additionalInfo(reason)
                .build();
    }

    public static ParseResult buffered(long lineNumber, String rawLine) {
        return ParseResult.builder()
                .status(Status.BUFFERED)
                .lineNumber(lineNumber)
                .rawLine(rawLine)
                .build();
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isContinuation() {
        return status == Status.CONTINUATION;
    }

    public boolean isSkipped() {
        return status == Status.SKIPPED;
    }

    public boolean isBuffered() {
        return status == Status.BUFFERED;
    }

    public boolean shouldCountAsSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean shouldCountAsFailure() {
        return status == Status.FAILED;
    }
    
    @Override
    public String toString() {
        return String.format("ParseResult{status=%s, line=%d, error='%s'}", 
                status, lineNumber, errorMessage != null ? errorMessage : "none");
    }
}
