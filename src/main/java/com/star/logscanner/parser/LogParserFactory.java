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

@Component
@Slf4j
public class LogParserFactory {

    private final List<LogParser> parsers;

    private final Map<String, LogParser> parserCache = new ConcurrentHashMap<>();

    private static final int SAMPLE_LINES = 10;

    private static final int MAX_SAMPLE_CHARS = 4096;

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

    public LogParser getParserForFile(Path filePath) {
        String fileName = filePath.getFileName().toString();
        String contentSample = sampleFileContent(filePath);
        
        return getParser(fileName, contentSample);
    }

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

    public LogParser getParser(String format) {
        return getParserByFormat(format).orElse(null);
    }

    public List<String> getAvailableFormats() {
        return parsers.stream()
                .map(LogParser::getSupportedFormat)
                .collect(Collectors.toList());
    }

    public List<LogParser> getAllParsers() {
        return List.copyOf(parsers);
    }

    public void registerParser(LogParser parser) {
        parsers.add(parser);
        // Re-sort by priority
        parsers.sort(Comparator.comparingInt(LogParser::getPriority).reversed());
        log.info("Registered new parser: {} with priority {}", 
                parser.getSupportedFormat(), parser.getPriority());
    }

    public boolean unregisterParser(String format) {
        boolean removed = parsers.removeIf(
                p -> p.getSupportedFormat().equalsIgnoreCase(format));
        
        if (removed) {
            parserCache.remove(format.toUpperCase());
            log.info("Unregistered parser: {}", format);
        }
        
        return removed;
    }

    public boolean hasParser(String format) {
        return getParserByFormat(format).isPresent();
    }

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
                
                if (!sample.isEmpty()) {
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

    public ParseContext createContext(Path filePath, String jobId, String timestampFormat) {
        return ParseContext.builder()
                .jobId(jobId)
                .fileName(filePath != null ? filePath.getFileName().toString() : null)
                .timestampFormat(timestampFormat)
                .build();
    }

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
