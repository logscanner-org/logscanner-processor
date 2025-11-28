package com.star.logscanner.parser;

import com.star.logscanner.entity.LogEntry;

/**
 * Strategy interface for log parsing implementations.
 * Each parser implementation handles a specific log format (text, CSV, JSON, etc.)
 * 
 * <p>Design Pattern: Strategy Pattern - allows interchangeable parsing algorithms
 * 
 * <p>Usage:
 * <pre>{@code
 * LogParser parser = factory.getParser(fileName, contentSample);
 * ParseContext context = new ParseContext(timestampFormat);
 * 
 * for (String line : lines) {
 *     ParseResult result = parser.parseLine(line, lineNumber++, context);
 *     if (result.isSuccess()) {
 *         processEntry(result.getEntry());
 *     }
 * }
 * parser.reset(); // Reset for next file
 * }</pre>
 * 
 * @author LogScanner Team
 * @version 2.0
 */
public interface LogParser {
    
    /**
     * Determines if this parser can handle the given file based on 
     * filename and content sample.
     * 
     * @param fileName the name of the file (used for extension detection)
     * @param contentSample a sample of the file content (first few lines)
     * @return true if this parser can handle the file format
     */
    boolean canParse(String fileName, String contentSample);
    
    /**
     * Legacy method for backward compatibility.
     * Determines if this parser can handle a single line.
     * 
     * @param line the log line to check
     * @return true if this parser can parse the line
     */
    default boolean canParse(String line) {
        return canParse(null, line);
    }
    
    /**
     * Parse a single log line into a structured LogEntry.
     * 
     * @param line the raw log line to parse
     * @param lineNumber the line number in the source file (1-based)
     * @param context parsing context containing configuration and state
     * @return ParseResult containing the parsed entry or error information
     */
    ParseResult parseLine(String line, long lineNumber, ParseContext context);
    
    /**
     * Legacy method for backward compatibility.
     * Parse a log line with a timestamp format string.
     * 
     * @param line the raw log line
     * @param lineNumber the line number
     * @param timestampFormat the timestamp format pattern
     * @return parsed LogEntry or null if parsing fails
     */
    default LogEntry parseLine(String line, long lineNumber, String timestampFormat) {
        ParseContext context = new ParseContext(timestampFormat);
        ParseResult result = parseLine(line, lineNumber, context);
        return result.isSuccess() ? result.getEntry() : null;
    }
    
    /**
     * Reset parser state. Called between files for stateful parsers
     * that track multi-line entries (stack traces, etc.)
     */
    void reset();
    
    /**
     * Get the format identifier for this parser.
     * 
     * @return format identifier (e.g., "TEXT", "CSV", "JSON", "NDJSON")
     */
    String getSupportedFormat();
    
    /**
     * Legacy method name alias for backward compatibility.
     * 
     * @return parser type identifier
     */
    default String getParserType() {
        return getSupportedFormat();
    }
    
    /**
     * Get the priority of this parser for auto-detection.
     * Higher values = higher priority (checked first).
     * 
     * @return priority value (default: 0)
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * Check if this parser supports multi-line log entries.
     * 
     * @return true if the parser handles multi-line entries
     */
    default boolean supportsMultiLine() {
        return false;
    }
    
    /**
     * Get a description of the log formats this parser handles.
     * Used for documentation and debugging.
     * 
     * @return human-readable description of supported formats
     */
    default String getDescription() {
        return "Log parser for " + getSupportedFormat() + " format";
    }
}
