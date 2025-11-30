package com.star.logscanner.parser;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.star.logscanner.entity.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Parser for CSV and TSV log files.
 * 
 * <p>Features:
 * <ul>
 *   <li>Auto-detects column mappings based on header names</li>
 *   <li>Supports common delimiters (comma, tab, semicolon, pipe)</li>
 *   <li>Handles quoted fields and escaped delimiters</li>
 *   <li>Maps standard fields (timestamp, level, message, logger, thread)</li>
 *   <li>Stores unmapped columns in metadata</li>
 *   <li>Type conversion (strings to timestamps, numbers, etc.)</li>
 * </ul>
 * 
 * <p>Supported column names (case-insensitive):
 * <ul>
 *   <li>Timestamp: timestamp, time, date, datetime, @timestamp, log_time</li>
 *   <li>Level: level, severity, log_level, loglevel, priority</li>
 *   <li>Message: message, msg, text, log_message, description, content</li>
 *   <li>Logger: logger, logger_name, class, category, source</li>
 *   <li>Thread: thread, thread_name, thread_id</li>
 * </ul>
 * 
 * @author Eshmamatov Obidjon
 */
@Component
@Slf4j
public class CsvLogParser implements LogParser {

    private static final Set<String> TIMESTAMP_COLUMNS = Set.of(
            "timestamp", "time", "date", "datetime", "@timestamp",
            "log_time", "logtime", "created_at", "createdat", "ts"
    );

    private static final Set<String> LEVEL_COLUMNS = Set.of(
            "level", "severity", "log_level", "loglevel", "levelname",
            "priority", "log_severity"
    );

    private static final Set<String> MESSAGE_COLUMNS = Set.of(
            "message", "msg", "text", "log_message", "logmessage",
            "description", "content", "body", "log"
    );

    private static final Set<String> LOGGER_COLUMNS = Set.of(
            "logger", "logger_name", "loggername", "class", "classname",
            "category", "source", "component", "module"
    );

    private static final Set<String> THREAD_COLUMNS = Set.of(
            "thread", "thread_name", "threadname", "thread_id", "threadid"
    );

    private static final Set<String> HOSTNAME_COLUMNS = Set.of(
            "hostname", "host", "server", "machine", "node", "instance"
    );

    private static final Set<String> APPLICATION_COLUMNS = Set.of(
            "application", "app", "service", "service_name", "servicename", "app_name"
    );

    private static final Set<String> ENVIRONMENT_COLUMNS = Set.of(
            "environment", "env", "stage", "deployment"
    );

    private static final Set<String> STACK_TRACE_COLUMNS = Set.of(
            "stack_trace", "stacktrace", "exception", "error_stack", "traceback"
    );

    private static final List<DateTimeFormatter> TIMESTAMP_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME
    );

    private CSVParser csvParser;
    private char detectedDelimiter = ',';
    private String[] headers;
    private Map<String, Integer> columnIndexMap;
    private boolean headersProcessed = false;

    private int timestampIndex = -1;
    private int levelIndex = -1;
    private int messageIndex = -1;
    private int loggerIndex = -1;
    private int threadIndex = -1;
    private int hostnameIndex = -1;
    private int applicationIndex = -1;
    private int environmentIndex = -1;
    private int stackTraceIndex = -1;

    @Override
    public boolean canParse(String fileName, String contentSample) {
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".csv") || lower.endsWith(".tsv")) {
                return true;
            }
        }

        if (contentSample != null && !contentSample.trim().isEmpty()) {
            // Check if it looks like CSV/TSV (not JSON)
            String trimmed = contentSample.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return false; // JSON
            }

            // Check for delimiter patterns
            String firstLine = trimmed.split("\n")[0];
            return detectDelimiter(firstLine) != '\0';
        }

        return false;
    }

    @Override
    public ParseResult parseLine(String line, long lineNumber, ParseContext context) {
        if (line == null || line.trim().isEmpty()) {
            return ParseResult.skipped(lineNumber, "Empty line");
        }

        try {
            // Initialize parser if needed
            if (csvParser == null) {
                initializeParser(line, context);
            }

            // Parse the CSV line
            String[] values = csvParser.parseLine(line);

            // Handle headers on first line
            if (!headersProcessed) {
                if (isHeaderRow(values)) {
                    processHeaders(values, context);
                    return ParseResult.skipped(lineNumber, "Header row");
                } else {
                    // No headers, use context headers or generate defaults
                    if (context.getCsvHeaders() != null) {
                        processHeaders(context.getCsvHeaders(), context);
                    } else {
                        generateDefaultHeaders(values.length, context);
                    }
                }
            }

            // Parse data row
            LogEntry entry = parseDataRow(values, lineNumber, context);
            if (entry != null) {
                return ParseResult.success(entry);
            } else {
                return ParseResult.failed(lineNumber, line, "Failed to parse CSV row");
            }

        } catch (Exception e) {
            log.debug("Failed to parse CSV line {}: {}", lineNumber, e.getMessage());
            return ParseResult.failed(lineNumber, line, e.getMessage());
        }
    }

    @Override
    public void reset() {
        csvParser = null;
        detectedDelimiter = ',';
        headers = null;
        columnIndexMap = null;
        headersProcessed = false;
        resetColumnIndices();
    }

    @Override
    public String getSupportedFormat() {
        return "CSV";
    }

    @Override
    public int getPriority() {
        return 10; // Higher than text parser
    }

    @Override
    public boolean supportsMultiLine() {
        return false; // CSV rows are single lines (quoted newlines handled by parser)
    }

    @Override
    public String getDescription() {
        return "CSV/TSV parser with auto-detection of columns and delimiters";
    }

    private void initializeParser(String sampleLine, ParseContext context) {
        // Detect delimiter
        detectedDelimiter = context.getOption("csv.delimiter", '\0');
        if (detectedDelimiter == '\0') {
            detectedDelimiter = detectDelimiter(sampleLine);
            if (detectedDelimiter == '\0') {
                detectedDelimiter = ','; // Default to comma
            }
        }

        // Get quote character
        char quoteChar = context.getOption("csv.quote", '"');

        // Build parser
        csvParser = new CSVParserBuilder()
                .withSeparator(detectedDelimiter)
                .withQuoteChar(quoteChar)
                .withIgnoreLeadingWhiteSpace(true)
                .build();

        log.debug("Initialized CSV parser with delimiter='{}', quote='{}'",
                detectedDelimiter == '\t' ? "\\t" : detectedDelimiter, quoteChar);
    }

    private char detectDelimiter(String line) {
        if (line == null || line.isEmpty()) {
            return '\0';
        }

        // Count occurrences of common delimiters
        int commas = countOccurrences(line, ',');
        int tabs = countOccurrences(line, '\t');
        int semicolons = countOccurrences(line, ';');
        int pipes = countOccurrences(line, '|');

        // Pick the most common delimiter (with minimum threshold)
        int max = Math.max(Math.max(commas, tabs), Math.max(semicolons, pipes));

        if (max < 1) {
            return '\0'; // No clear delimiter found
        }

        if (tabs == max) return '\t';
        if (commas == max) return ',';
        if (semicolons == max) return ';';
        if (pipes == max) return '|';

        return ','; // Default
    }

    private int countOccurrences(String str, char c) {
        int count = 0;
        boolean inQuotes = false;

        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == c && !inQuotes) {
                count++;
            }
        }

        return count;
    }

    private boolean isHeaderRow(String[] values) {
        if (values == null || values.length == 0) {
            return false;
        }

        // Check if any value matches known column names
        for (String value : values) {
            String lower = value.toLowerCase().trim();
            if (TIMESTAMP_COLUMNS.contains(lower) ||
                    LEVEL_COLUMNS.contains(lower) ||
                    MESSAGE_COLUMNS.contains(lower) ||
                    LOGGER_COLUMNS.contains(lower)) {
                return true;
            }
        }

        // Also consider it a header if all values are non-numeric
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                try {
                    Double.parseDouble(value);
                    return false; // Found a number, probably not a header
                } catch (NumberFormatException e) {
                    // Not a number, continue checking
                }
            }
        }

        return true; // All strings, likely a header
    }

    private void processHeaders(String[] headerRow, ParseContext context) {
        headers = new String[headerRow.length];
        columnIndexMap = new HashMap<>();

        for (int i = 0; i < headerRow.length; i++) {
            String header = headerRow[i].trim();
            headers[i] = header;

            String lower = header.toLowerCase();
            columnIndexMap.put(lower, i);

            // Map to standard fields
            if (TIMESTAMP_COLUMNS.contains(lower)) {
                timestampIndex = i;
            } else if (LEVEL_COLUMNS.contains(lower)) {
                levelIndex = i;
            } else if (MESSAGE_COLUMNS.contains(lower)) {
                messageIndex = i;
            } else if (LOGGER_COLUMNS.contains(lower)) {
                loggerIndex = i;
            } else if (THREAD_COLUMNS.contains(lower)) {
                threadIndex = i;
            } else if (HOSTNAME_COLUMNS.contains(lower)) {
                hostnameIndex = i;
            } else if (APPLICATION_COLUMNS.contains(lower)) {
                applicationIndex = i;
            } else if (ENVIRONMENT_COLUMNS.contains(lower)) {
                environmentIndex = i;
            } else if (STACK_TRACE_COLUMNS.contains(lower)) {
                stackTraceIndex = i;
            }
        }

        // Store in context for persistence
        context.setCsvHeaders(headers);
        context.setCsvColumnMappings(columnIndexMap);
        context.setCsvHeadersProcessed(true);
        headersProcessed = true;

        log.debug("Processed CSV headers: {} (timestamp={}, level={}, message={})",
                Arrays.toString(headers), timestampIndex, levelIndex, messageIndex);
    }

    private void generateDefaultHeaders(int columnCount, ParseContext context) {
        headers = new String[columnCount];
        columnIndexMap = new HashMap<>();

        for (int i = 0; i < columnCount; i++) {
            headers[i] = "column_" + i;
            columnIndexMap.put(headers[i].toLowerCase(), i);
        }

        // Try to infer column types from first data value positions
        // Assume common patterns: timestamp first, then level, then message
        if (columnCount >= 1) timestampIndex = 0;
        if (columnCount >= 2) levelIndex = 1;
        if (columnCount >= 3) messageIndex = 2;

        context.setCsvHeaders(headers);
        context.setCsvColumnMappings(columnIndexMap);
        context.setCsvHeadersProcessed(true);
        headersProcessed = true;

        log.debug("Generated default CSV headers for {} columns", columnCount);
    }

    private void resetColumnIndices() {
        timestampIndex = -1;
        levelIndex = -1;
        messageIndex = -1;
        loggerIndex = -1;
        threadIndex = -1;
        hostnameIndex = -1;
        applicationIndex = -1;
        environmentIndex = -1;
        stackTraceIndex = -1;
    }

    private LogEntry parseDataRow(String[] values, long lineNumber, ParseContext context) {
        LogEntry.LogEntryBuilder builder = LogEntry.builder()
                .id(UUID.randomUUID().toString())
                .lineNumber(lineNumber)
                .rawLine(String.join(String.valueOf(detectedDelimiter), values))
                .indexedAt(java.time.Instant.now());

        // Set job/file info
        if (context.getJobId() != null) {
            builder.jobId(context.getJobId());
        }
        if (context.getFileName() != null) {
            builder.fileName(context.getFileName());
        }

        // Map standard fields
        builder.timestamp(getTimestamp(values, context));
        builder.level(getLevel(values));
        builder.message(getMessage(values));
        builder.logger(getValueAt(values, loggerIndex));
        builder.thread(getValueAt(values, threadIndex));
        builder.hostname(getValueAt(values, hostnameIndex));
        builder.application(getValueAt(values, applicationIndex));
        builder.environment(getValueAt(values, environmentIndex));

        // Handle stack trace
        String stackTrace = getValueAt(values, stackTraceIndex);
        if (stackTrace != null && !stackTrace.isEmpty()) {
            builder.stackTrace(stackTrace);
            builder.hasStackTrace(true);
        }

        // Set error flag
        String level = builder.build().getLevel();
        builder.hasError("ERROR".equals(level) || "FATAL".equals(level));

        // Collect remaining fields as metadata
        Map<String, Object> metadata = collectMetadata(values);
        if (!metadata.isEmpty()) {
            builder.metadata(metadata);
        }

        return builder.build();
    }

    private LocalDateTime getTimestamp(String[] values, ParseContext context) {
        String value = getValueAt(values, timestampIndex);
        if (value == null || value.isEmpty()) {
            return LocalDateTime.now();
        }

        return parseTimestamp(value, context);
    }

    private String getLevel(String[] values) {
        String value = getValueAt(values, levelIndex);
        if (value == null || value.isEmpty()) {
            return "INFO";
        }
        return normalizeLogLevel(value);
    }

    private String getMessage(String[] values) {
        String value = getValueAt(values, messageIndex);
        if (value != null) {
            return value;
        }

        // If no message column, concatenate remaining columns
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i != timestampIndex && i != levelIndex &&
                    i != loggerIndex && i != threadIndex) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(values[i]);
            }
        }
        return !sb.isEmpty() ? sb.toString() : "";
    }

    private String getValueAt(String[] values, int index) {
        if (index < 0 || index >= values.length) {
            return null;
        }
        String value = values[index];
        return (value != null && !value.isEmpty()) ? value.trim() : null;
    }

    private Map<String, Object> collectMetadata(String[] values) {
        Map<String, Object> metadata = new HashMap<>();

        Set<Integer> standardIndices = Set.of(
                timestampIndex, levelIndex, messageIndex, loggerIndex,
                threadIndex, hostnameIndex, applicationIndex,
                environmentIndex, stackTraceIndex
        );

        for (int i = 0; i < values.length && i < headers.length; i++) {
            if (!standardIndices.contains(i)) {
                String value = values[i];
                if (value != null && !value.isEmpty()) {
                    // Try type conversion
                    Object converted = convertValue(value);
                    metadata.put(headers[i], converted);
                }
            }
        }

        return metadata;
    }

    private Object convertValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        // Try boolean
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }

        // Try integer
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Not an integer
        }

        // Try long
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            // Not a long
        }

        // Try double
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // Not a double
        }

        // Return as string
        return value;
    }

    private LocalDateTime parseTimestamp(String value, ParseContext context) {
        if (value == null || value.isEmpty()) {
            return LocalDateTime.now();
        }

        value = value.trim();

        // Try context-provided formatter
        if (context.getTimestampFormatter() != null) {
            try {
                return LocalDateTime.parse(value, context.getTimestampFormatter());
            } catch (DateTimeParseException e) {
                // Continue with auto-detection
            }
        }

        // Try epoch milliseconds
        try {
            long epoch = Long.parseLong(value);
            if (epoch > 1_000_000_000_000L) {
                // Milliseconds
                return LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(epoch), ZoneId.systemDefault());
            } else {
                // Seconds
                return LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
            }
        } catch (NumberFormatException e) {
            // Not epoch
        }

        // Try ISO instant
        try {
            Instant instant = Instant.parse(value);
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        } catch (Exception e) {
            // Continue
        }

        // Try common formatters
        for (DateTimeFormatter formatter : TIMESTAMP_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException e) {
                // Try next
            }
        }

        log.debug("Could not parse timestamp: '{}', using current time", value);
        return LocalDateTime.now();
    }

    private String normalizeLogLevel(String level) {
        if (level == null) return "INFO";

        return switch (level.toUpperCase().trim()) {
            case "WARNING", "WARN" -> "WARN";
            case "SEVERE", "FATAL", "CRITICAL", "ALERT", "EMERGENCY" -> "ERROR";
            case "FINE", "FINER", "FINEST", "VERBOSE", "DBG" -> "DEBUG";
            case "CONFIG", "NOTICE", "INFORMATIONAL" -> "INFO";
            case "TRC" -> "TRACE";
            default -> level.toUpperCase().trim();
        };
    }

    public void setColumnMapping(String columnName, FieldType fieldType) {
        if (columnIndexMap == null) {
            columnIndexMap = new HashMap<>();
        }

        Integer index = columnIndexMap.get(columnName.toLowerCase());
        if (index != null) {
            switch (fieldType) {
                case TIMESTAMP -> timestampIndex = index;
                case LEVEL -> levelIndex = index;
                case MESSAGE -> messageIndex = index;
                case LOGGER -> loggerIndex = index;
                case THREAD -> threadIndex = index;
                case HOSTNAME -> hostnameIndex = index;
                case APPLICATION -> applicationIndex = index;
                case ENVIRONMENT -> environmentIndex = index;
                case STACK_TRACE -> stackTraceIndex = index;
            }
        }
    }

    public enum FieldType {
        TIMESTAMP, LEVEL, MESSAGE, LOGGER, THREAD,
        HOSTNAME, APPLICATION, ENVIRONMENT, STACK_TRACE
    }
}
