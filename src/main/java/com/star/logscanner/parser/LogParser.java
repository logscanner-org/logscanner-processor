package com.star.logscanner.parser;

import com.star.logscanner.entity.LogEntry;

public interface LogParser {
    
    /**
     * Parse a log line into a LogEntry
     * 
     * @param line The raw log line
     * @param lineNumber The line number in the file
     * @param timestampFormat The timestamp format pattern
     * @return Parsed LogEntry or null if parsing fails
     */
    LogEntry parseLine(String line, long lineNumber, String timestampFormat);
    
    /**
     * Check if this parser can handle the given log line
     * 
     * @param line The log line to check
     * @return true if this parser can handle the line
     */
    boolean canParse(String line);
    
    /**
     * Get the parser type name
     * 
     * @return Parser type identifier
     */
    String getParserType();
}
