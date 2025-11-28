package com.star.logscanner.parser;

import com.star.logscanner.entity.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @deprecated Use {@link TextLogParser} instead. This class is maintained
 * for backward compatibility only.
 * 
 * <p>StandardLogParser now delegates to TextLogParser which provides
 * enhanced multi-format support including Log4j, Logback, Apache, Syslog,
 * and custom patterns.
 * 
 * @see TextLogParser
 */
@Component
@Slf4j
@Deprecated(since = "2.0", forRemoval = true)
public class StandardLogParser implements LogParser {
    
    private final TextLogParser delegate;
    
    public StandardLogParser() {
        this.delegate = new TextLogParser();
    }
    
    @Override
    public boolean canParse(String fileName, String contentSample) {
        return delegate.canParse(fileName, contentSample);
    }
    
    @Override
    public ParseResult parseLine(String line, long lineNumber, ParseContext context) {
        return delegate.parseLine(line, lineNumber, context);
    }
    
    @Override
    public LogEntry parseLine(String line, long lineNumber, String timestampFormat) {
        return delegate.parseLine(line, lineNumber, timestampFormat);
    }
    
    @Override
    public void reset() {
        delegate.reset();
    }
    
    @Override
    public String getSupportedFormat() {
        return "STANDARD"; // Keep original name for backward compatibility
    }
    
    @Override
    public String getParserType() {
        return "STANDARD";
    }
    
    @Override
    public int getPriority() {
        return -1; // Lower than TextLogParser to avoid conflicts
    }
    
    @Override
    public boolean supportsMultiLine() {
        return delegate.supportsMultiLine();
    }
    
    @Override
    public String getDescription() {
        return "Standard log parser (deprecated - use TextLogParser)";
    }
}
