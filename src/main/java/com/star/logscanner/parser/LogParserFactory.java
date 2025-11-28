package com.star.logscanner.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Factory for creating and selecting appropriate log parsers.
 * 
 * <p>Design Pattern: Factory Pattern - centralizes parser creation and selection
 * 
 * <p>The factory supports:
 * <ul>
 *   <li>Auto-detection based on file extension and content</li>
 *   <li>Parser registration and management</li>
 *   <li>Singleton and prototype scoped parsers</li>
 *   <li>Priority-based parser selection</li>
 * </ul>
 * 
 * <p>Usage:
 * <pre>{@code
 * LogParser parser = factory.getParserForFile(filePath);
 * // or
 * LogParser parser = factory.getParser("JSON");
 * }</pre>
 * 
 * @author LogScanner Team
 * @version 2.0
 */
@Component
@Slf4j
public class LogParserFactory {
    
    /**
     * Registered parsers, sorted by priority (highest first).
     */
    private final List<LogParser> parsers;
    
    /**
     * Parser cache for singleton parsers.
     */
    private final Map<String, LogParser> parserCache = new ConcurrentHashMap<>();
    
    /**
     * Number of sample lines to read for content detection.
     */
    private static final int SAMPLE_LINES = 10;
    
    /**
     * Maximum characters to read for content sampling.
     */
    private static final int MAX_SAMPLE_CHARS = 4096;
    
    /**
     * Constructor with automatic parser discovery via Spring DI.
     * 
     * @param parsers list of available parser implementations
     */
    public LogParserFactory(List<LogParser> parsers) {
        // Sort parsers by priority (highest first)
        this.parsers = parsers.stream()
                .sorted(Comparator.comparingInt(LogParser::getPriority).reversed())
                .collect(Collectors.toList());
        
        log.info("LogParserFactory initialized with {} parsers: {}", 
                parsers.size(),
                parsers.stream()
                       .map(p -> p.getSupportedFormat() + "(" + p.getPriority() + ")")
                       .collect(Collectors.joining(", ")));
    }
    
    /**
     * Get the appropriate parser for a file path.
     * Performs content sampling for auto-detection.
     * 
     * @param filePath path to the log file
     * @return appropriate LogParser
     * @throws IllegalArgumentException if no parser can handle the file
     */
    public LogParser getParserForFile(Path filePath) {
        String fileName = filePath.getFileName().toString();
        String contentSample = sampleFileContent(filePath);
        
        return getParser(fileName, contentSample);
    }
    
    /**
     * Get the appropriate parser based on filename and content sample.
     * 
     * @param fileName the filename (for extension detection)
     * @param contentSample sample of file content
     * @return appropriate LogParser
     * @throws IllegalArgumentException if no parser can handle the file
     */
    public LogParser getParser(String fileName, String contentSample) {
        log.debug("Selecting parser for file: {}", fileName);
        
        // First, try extension-based detection for efficiency
        Optional<LogParser> extensionMatch = detectByExtension(fileName);
        if (extensionMatch.isPresent()) {
            LogParser parser = extensionMatch.get();
            // Verify the parser can actually handle the content
            if (parser.canParse(fileName, contentSample)) {
                log.debug("Selected {} parser based on extension", parser.getSupportedFormat());
                return parser;
            }
        }
        
        // Fall back to content-based detection
        for (LogParser parser : parsers) {
            if (parser.canParse(fileName, contentSample)) {
                log.debug("Selected {} parser based on content detection", parser.getSupportedFormat());
                return parser;
            }
        }
        
        // Last resort: use the text parser if available
        Optional<LogParser> textParser = getParserByFormat("TEXT");
        if (textParser.isPresent()) {
            log.warn("No specific parser found for {}, falling back to TEXT parser", fileName);
            return textParser.get();
        }
        
        throw new IllegalArgumentException(
                "No suitable parser found for file: " + fileName + 
                ". Available parsers: " + getAvailableFormats());
    }
    
    /**
     * Get a parser by its format identifier.
     * 
     * @param format the format identifier (e.g., "JSON", "CSV", "TEXT")
     * @return Optional containing the parser if found
     */
    public Optional<LogParser> getParserByFormat(String format) {
        if (format == null) {
            return Optional.empty();
        }
        
        String normalizedFormat = format.toUpperCase();
        
        // Check cache first
        LogParser cached = parserCache.get(normalizedFormat);
        if (cached != null) {
            return Optional.of(cached);
        }
        
        // Find and cache
        Optional<LogParser> parser = parsers.stream()
                .filter(p -> p.getSupportedFormat().equalsIgnoreCase(normalizedFormat))
                .findFirst();
        
        parser.ifPresent(p -> parserCache.put(normalizedFormat, p));
        
        return parser;
    }
    
    /**
     * Legacy method - get parser by format string.
     * 
     * @param format format identifier
     * @return LogParser or null if not found
     */
    public LogParser getParser(String format) {
        return getParserByFormat(format).orElse(null);
    }
    
    /**
     * Get list of all available format identifiers.
     * 
     * @return list of supported format strings
     */
    public List<String> getAvailableFormats() {
        return parsers.stream()
                .map(LogParser::getSupportedFormat)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all registered parsers.
     * 
     * @return unmodifiable list of parsers
     */
    public List<LogParser> getAllParsers() {
        return List.copyOf(parsers);
    }
    
    /**
     * Register a new parser dynamically.
     * 
     * @param parser the parser to register
     */
    public void registerParser(LogParser parser) {
        parsers.add(parser);
        // Re-sort by priority
        parsers.sort(Comparator.comparingInt(LogParser::getPriority).reversed());
        log.info("Registered new parser: {} with priority {}", 
                parser.getSupportedFormat(), parser.getPriority());
    }
    
    /**
     * Unregister a parser by format.
     * 
     * @param format the format identifier to remove
     * @return true if parser was removed
     */
    public boolean unregisterParser(String format) {
        boolean removed = parsers.removeIf(
                p -> p.getSupportedFormat().equalsIgnoreCase(format));
        
        if (removed) {
            parserCache.remove(format.toUpperCase());
            log.info("Unregistered parser: {}", format);
        }
        
        return removed;
    }
    
    /**
     * Check if a parser exists for the given format.
     * 
     * @param format format identifier
     * @return true if parser is available
     */
    public boolean hasParser(String format) {
        return getParserByFormat(format).isPresent();
    }
    
    // ========== Private Methods ==========
    
    /**
     * Detect parser based on file extension.
     */
    private Optional<LogParser> detectByExtension(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        
        String lowerName = fileName.toLowerCase();
        
        if (lowerName.endsWith(".json") || lowerName.endsWith(".ndjson")) {
            return getParserByFormat("JSON");
        }
        
        if (lowerName.endsWith(".csv") || lowerName.endsWith(".tsv")) {
            return getParserByFormat("CSV");
        }
        
        if (lowerName.endsWith(".log") || lowerName.endsWith(".txt") || 
            lowerName.endsWith(".out") || lowerName.endsWith(".err")) {
            return getParserByFormat("TEXT");
        }
        
        return Optional.empty();
    }
    
    /**
     * Sample the first few lines of a file for content detection.
     */
    private String sampleFileContent(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            return "";
        }
        
        StringBuilder sample = new StringBuilder();
        
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            int linesRead = 0;
            int charsRead = 0;
            String line;
            
            while ((line = reader.readLine()) != null && 
                   linesRead < SAMPLE_LINES && 
                   charsRead < MAX_SAMPLE_CHARS) {
                
                if (sample.length() > 0) {
                    sample.append("\n");
                }
                sample.append(line);
                
                linesRead++;
                charsRead += line.length();
            }
            
        } catch (IOException e) {
            log.warn("Failed to sample file content: {}", filePath, e);
        }
        
        return sample.toString();
    }
    
    /**
     * Create a parser context with default settings for the given file.
     * 
     * @param filePath path to the file
     * @param jobId job identifier
     * @param timestampFormat custom timestamp format (optional)
     * @return configured ParseContext
     */
    public ParseContext createContext(Path filePath, String jobId, String timestampFormat) {
        return ParseContext.builder()
                .jobId(jobId)
                .fileName(filePath != null ? filePath.getFileName().toString() : null)
                .timestampFormat(timestampFormat)
                .build();
    }
    
    /**
     * Get parser info for debugging/monitoring.
     * 
     * @return map of parser format to description
     */
    public Map<String, String> getParserInfo() {
        return parsers.stream()
                .collect(Collectors.toMap(
                        LogParser::getSupportedFormat,
                        p -> String.format("%s (priority: %d, multiline: %s)", 
                                p.getDescription(), 
                                p.getPriority(), 
                                p.supportsMultiLine())
                ));
    }
}
