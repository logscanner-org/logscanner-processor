package com.star.logscanner.processor;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
public class FileStreamProcessor {
    
    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024; // 8KB
    private static final int PROGRESS_INTERVAL = 1000; // Report progress every N lines
    
    private final int bufferSize;
    private final Charset charset;
    private final BiConsumer<Long, Long> onProgress;
    private final Consumer<Exception> onError;
    private final int progressInterval;
    
    @Getter
    public static class ProcessingStats {
        private long totalLines;
        private long bytesRead;
        private long processingTimeMs;
        private long startLine;
        private long endLine;

        public double getLinesPerSecond() {
            return processingTimeMs > 0 ? (totalLines * 1000.0) / processingTimeMs : 0;
        }
        
        public double getBytesPerSecond() {
            return processingTimeMs > 0 ? (bytesRead * 1000.0) / processingTimeMs : 0;
        }
        
        @Override
        public String toString() {
            return String.format(
                    "ProcessingStats{lines=%d, bytes=%d, timeMs=%d, lps=%.2f}",
                    totalLines, bytesRead, processingTimeMs, getLinesPerSecond());
        }
    }
    
    private FileStreamProcessor(Builder builder) {
        this.bufferSize = builder.bufferSize;
        this.charset = builder.charset;
        this.onProgress = builder.onProgress;
        this.onError = builder.onError;
        this.progressInterval = builder.progressInterval;
    }

    // Process a file line by line.
    public ProcessingStats processFile(Path filePath, 
                                        BiConsumer<String, Long> lineHandler) throws IOException {
        return processFile(filePath, lineHandler, 1);
    }
    
    // Process a file starting from a specific line number.
    public ProcessingStats processFile(Path filePath, 
                                        BiConsumer<String, Long> lineHandler,
                                        long startLine) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filePath);
        }
        
        ProcessingStats stats = new ProcessingStats();
        stats.startLine = startLine;
        
        long totalLines = 0;
        
        // First pass: count lines (optional, can be disabled for huge files)
        if (onProgress != null) {
            totalLines = countLines(filePath);
            log.debug("File has {} lines", totalLines);
        }
        
        // Second pass: process lines
        long startTime = System.currentTimeMillis();
        long lineNumber = 0;
        long processedLines = 0;
        
        try (BufferedReader reader = Files.newBufferedReader(filePath, charset)) {
            // If using custom buffer size, wrap in BufferedReader with specified size
            String line;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // Skip lines before startLine
                if (lineNumber < startLine) {
                    continue;
                }
                
                try {
                    lineHandler.accept(line, lineNumber);
                    processedLines++;
                    stats.bytesRead += line.length() + 1; // +1 for newline
                    
                    // Report progress
                    if (onProgress != null && processedLines % progressInterval == 0) {
                        onProgress.accept(lineNumber, totalLines);
                    }
                    
                } catch (Exception e) {
                    log.debug("Error processing line {}: {}", lineNumber, e.getMessage());
                    if (onError != null) {
                        onError.accept(e);
                    }
                }
            }
        }
        
        stats.totalLines = processedLines;
        stats.endLine = lineNumber;
        stats.processingTimeMs = System.currentTimeMillis() - startTime;
        
        log.info("Processed {} lines in {} ms ({} lines/sec)",
                processedLines, stats.processingTimeMs, 
                String.format("%.2f", stats.getLinesPerSecond()));
        
        return stats;
    }

    public long countLines(Path filePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath, charset)) {
            return reader.lines().count();
        }
    }

    public long getFileSize(Path filePath) throws IOException {
        return Files.size(filePath);
    }

    public boolean isReadable(Path filePath) {
        return Files.exists(filePath) && Files.isReadable(filePath);
    }
    
    // Detect file encoding (basic detection).
    public Charset detectEncoding(Path filePath) {
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            
            // Check for BOM markers
            if (bytes.length >= 3 && 
                bytes[0] == (byte) 0xEF && 
                bytes[1] == (byte) 0xBB && 
                bytes[2] == (byte) 0xBF) {
                return StandardCharsets.UTF_8;
            }
            
            if (bytes.length >= 2) {
                if (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
                    return StandardCharsets.UTF_16BE;
                }
                if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
                    return StandardCharsets.UTF_16LE;
                }
            }
            
            // Default to UTF-8
            return StandardCharsets.UTF_8;
            
        } catch (IOException e) {
            log.debug("Could not detect encoding, using UTF-8: {}", e.getMessage());
            return StandardCharsets.UTF_8;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static FileStreamProcessor createDefault() {
        return builder().build();
    }
    
    public static class Builder {
        private int bufferSize = DEFAULT_BUFFER_SIZE;
        private Charset charset = StandardCharsets.UTF_8;
        private BiConsumer<Long, Long> onProgress;
        private Consumer<Exception> onError;
        private int progressInterval = PROGRESS_INTERVAL;
        
        // Set buffer size for reading.
        public Builder bufferSize(int bufferSize) {
            if (bufferSize < 1024) {
                throw new IllegalArgumentException("Buffer size must be at least 1024 bytes");
            }
            this.bufferSize = bufferSize;
            return this;
        }
        
        // Set character encoding.
        public Builder charset(Charset charset) {
            this.charset = charset != null ? charset : StandardCharsets.UTF_8;
            return this;
        }

        public Builder onProgress(BiConsumer<Long, Long> onProgress) {
            this.onProgress = onProgress;
            return this;
        }

        public Builder onError(Consumer<Exception> onError) {
            this.onError = onError;
            return this;
        }

        public Builder progressInterval(int interval) {
            this.progressInterval = Math.max(1, interval);
            return this;
        }

        public FileStreamProcessor build() {
            return new FileStreamProcessor(this);
        }
    }
}
