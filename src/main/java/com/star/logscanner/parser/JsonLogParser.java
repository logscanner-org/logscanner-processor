package com.star.logscanner.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.star.logscanner.entity.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Parser for JSON and NDJSON (newline-delimited JSON) log formats.
 *
 * <p>Features:
 * <ul>
 *   <li>Supports single JSON objects and NDJSON streams</li>
 *   <li>Automatic field mapping from common log schemas</li>
 *   <li>Nested JSON structure handling</li>
 *   <li>Graceful handling of malformed JSON</li>
 *   <li>Epoch and ISO timestamp parsing</li>
 * </ul>
 *
 * <p>Supported JSON schemas:
 * <ul>
 *   <li>Logstash/ELK format</li>
 *   <li>Bunyan format</li>
 *   <li>Winston format</li>
 *   <li>Custom formats with common field names</li>
 * </ul>
 *
 * @author Eshmamatov Obidjon
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JsonLogParser implements LogParser {

    private final ObjectMapper objectMapper;

    private static final String[] TIMESTAMP_FIELDS = {
            "timestamp", "time", "@timestamp", "datetime", "date", "ts", "log_time", "logTime"
    };

    private static final String[] LEVEL_FIELDS = {
            "level", "severity", "log_level", "logLevel", "loglevel", "levelname"
    };

    private static final String[] MESSAGE_FIELDS = {
            "message", "msg", "text", "log_message", "logMessage", "description"
    };

    private static final String[] LOGGER_FIELDS = {
            "logger", "logger_name", "loggerName", "class", "category", "name"
    };

    private static final String[] THREAD_FIELDS = {
            "thread", "thread_name", "threadName", "thread_id", "threadId"
    };

    private static final String[] STACK_TRACE_FIELDS = {
            "stack_trace", "stackTrace", "stack", "exception", "error_stack", "errorStack"
    };

    private static final String[] HOSTNAME_FIELDS = {
            "hostname", "host", "server", "instance", "machine", "node"
    };

    private static final String[] APPLICATION_FIELDS = {
            "application", "app", "service", "service_name", "serviceName", "app_name", "appName"
    };

    private static final String[] ENVIRONMENT_FIELDS = {
            "environment", "env", "stage", "deployment"
    };

    @Override
    public boolean canParse(String fileName, String contentSample) {
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".json") || lower.endsWith(".ndjson")) {
                return true;
            }
        }

        if (contentSample != null) {
            return isJson(contentSample.trim());
        }

        return false;
    }

    @Override
    public ParseResult parseLine(String line, long lineNumber, ParseContext context) {
        if (line == null) {
            return ParseResult.skipped(lineNumber, "Null line");
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return ParseResult.skipped(lineNumber, "Empty line");
        }

        if (!isJson(trimmed)) {
            return ParseResult.failed(lineNumber, line, "Not valid JSON");
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(trimmed);

            LogEntry entry = LogEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .lineNumber(lineNumber)
                    .rawLine(line)
                    .indexedAt(java.time.Instant.now())
                    .build();

            // Set job/file info from context
            if (context != null) {
                if (context.getJobId() != null) {
                    entry.setJobId(context.getJobId());
                }
                if (context.getFileName() != null) {
                    entry.setFileName(context.getFileName());
                }
            }

            String timestampFormat = context != null ? context.getTimestampFormat() : null;

            // Extract standard fields
            extractTimestamp(jsonNode, entry, timestampFormat);
            extractLevel(jsonNode, entry);
            extractMessage(jsonNode, entry);
            extractLogger(jsonNode, entry);
            extractThread(jsonNode, entry);
            extractStackTrace(jsonNode, entry);
            extractHostname(jsonNode, entry);
            extractApplication(jsonNode, entry);
            extractEnvironment(jsonNode, entry);

            // Extract all remaining fields as metadata
            extractMetadata(jsonNode, entry);

            return ParseResult.success(entry);

        } catch (JsonProcessingException e) {
            log.debug("Failed to parse JSON at line {}: {}", lineNumber, e.getMessage());
            return ParseResult.failed(lineNumber, line, "JSON parse error: " + e.getMessage());
        } catch (Exception e) {
            log.debug("Unexpected error parsing JSON at line {}: {}", lineNumber, e.getMessage());
            return ParseResult.failed(lineNumber, line, "Unexpected error: " + e.getMessage());
        }
    }

    @Override
    public void reset() {
        // JSON parser is stateless, nothing to reset
    }

    @Override
    public String getSupportedFormat() {
        return "JSON";
    }

    @Override
    public int getPriority() {
        return 20; // Higher priority than CSV and TEXT
    }

    @Override
    public boolean supportsMultiLine() {
        return false; // Each line is a complete JSON object
    }

    @Override
    public String getDescription() {
        return "JSON/NDJSON log parser with automatic schema detection";
    }

    private void extractTimestamp(JsonNode node, LogEntry entry, String timestampFormat) {
        String timestampStr = findFieldValue(node, TIMESTAMP_FIELDS);

        if (timestampStr != null) {
            LocalDateTime timestamp = parseTimestamp(timestampStr, timestampFormat);
            entry.setTimestamp(timestamp);
        } else {
            // Try to find numeric timestamp (epoch millis)
            for (String field : TIMESTAMP_FIELDS) {
                if (node.has(field) && node.get(field).isNumber()) {
                    long epochMillis = node.get(field).asLong();
                    entry.setTimestamp(LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(epochMillis),
                            ZoneId.systemDefault()
                    ));
                    break;
                }
            }
        }

        if (entry.getTimestamp() == null) {
            entry.setTimestamp(LocalDateTime.now());
        }
    }

    private void extractLevel(JsonNode node, LogEntry entry) {
        String level = findFieldValue(node, LEVEL_FIELDS);
        if (level != null) {
            entry.setLevel(normalizeLogLevel(level));
            entry.setHasError("ERROR".equals(entry.getLevel()) || "FATAL".equals(entry.getLevel()));
        }
    }

    private void extractMessage(JsonNode node, LogEntry entry) {
        String message = findFieldValue(node, MESSAGE_FIELDS);
        if (message != null) {
            entry.setMessage(message);
        }
    }

    private void extractLogger(JsonNode node, LogEntry entry) {
        String logger = findFieldValue(node, LOGGER_FIELDS);
        if (logger != null) {
            entry.setLogger(logger);
            // Extract source from logger
            String[] parts = logger.split("\\.");
            if (parts.length > 0) {
                entry.setSource(parts[parts.length - 1]);
            }
        }
    }

    private void extractThread(JsonNode node, LogEntry entry) {
        String thread = findFieldValue(node, THREAD_FIELDS);
        if (thread != null) {
            entry.setThread(thread);
        }
    }

    private void extractStackTrace(JsonNode node, LogEntry entry) {
        String stackTrace = findFieldValue(node, STACK_TRACE_FIELDS);
        if (stackTrace != null && !stackTrace.isEmpty()) {
            entry.setStackTrace(stackTrace);
            entry.setHasStackTrace(true);
            if (entry.getHasError() == null) {
                entry.setHasError(true);  // Assume stack trace indicates error
            }
        }
    }

    private void extractHostname(JsonNode node, LogEntry entry) {
        String hostname = findFieldValue(node, HOSTNAME_FIELDS);
        if (hostname != null) {
            entry.setHostname(hostname);
        }
    }

    private void extractApplication(JsonNode node, LogEntry entry) {
        String application = findFieldValue(node, APPLICATION_FIELDS);
        if (application != null) {
            entry.setApplication(application);
        }
    }

    private void extractEnvironment(JsonNode node, LogEntry entry) {
        String environment = findFieldValue(node, ENVIRONMENT_FIELDS);
        if (environment != null) {
            entry.setEnvironment(environment);
        }
    }

    private void extractMetadata(JsonNode node, LogEntry entry) {
        Map<String, Object> metadata = new HashMap<>();

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();

            // Skip already processed fields
            if (isStandardField(fieldName)) {
                continue;
            }

            JsonNode value = field.getValue();
            if (value.isTextual()) {
                metadata.put(fieldName, value.asText());
            } else if (value.isNumber()) {
                metadata.put(fieldName, value.numberValue());
            } else if (value.isBoolean()) {
                metadata.put(fieldName, value.asBoolean());
            } else if (value.isObject() || value.isArray()) {
                metadata.put(fieldName, value.toString());
            }
        }

        if (!metadata.isEmpty()) {
            entry.setMetadata(metadata);
        }
    }

    private String findFieldValue(JsonNode node, String[] fieldNames) {
        for (String field : fieldNames) {
            if (node.has(field)) {
                JsonNode value = node.get(field);
                if (value.isTextual()) {
                    return value.asText();
                } else if (!value.isNull()) {
                    return value.toString();
                }
            }
        }
        return null;
    }

    private boolean isStandardField(String fieldName) {
        for (String[] fields : new String[][]{
                TIMESTAMP_FIELDS, LEVEL_FIELDS, MESSAGE_FIELDS,
                LOGGER_FIELDS, THREAD_FIELDS, STACK_TRACE_FIELDS,
                HOSTNAME_FIELDS, APPLICATION_FIELDS, ENVIRONMENT_FIELDS
        }) {
            for (String field : fields) {
                if (field.equalsIgnoreCase(fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private LocalDateTime parseTimestamp(String timestampStr, String format) {
        if (timestampStr == null || timestampStr.isEmpty()) {
            return LocalDateTime.now();
        }

        // Try provided format
        if (format != null && !format.isEmpty()) {
            try {
                return LocalDateTime.parse(timestampStr, DateTimeFormatter.ofPattern(format));
            } catch (Exception e) {
                // Fall through to other attempts
            }
        }

        // Try ISO formats
        try {
            return LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            // Try other formats
        }

        // Try parsing as Instant (for ISO-8601 with timezone)
        try {
            Instant instant = Instant.parse(timestampStr);
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        } catch (Exception e) {
            // Try other formats
        }

        return LocalDateTime.now();
    }

    private String normalizeLogLevel(String level) {
        if (level == null) {
            return "INFO";
        }

        return switch (level.toUpperCase()) {
            case "WARNING", "WARN" -> "WARN";
            case "SEVERE", "FATAL", "CRITICAL" -> "ERROR";
            case "FINE", "FINER", "FINEST", "VERBOSE" -> "DEBUG";
            case "CONFIG", "NOTICE" -> "INFO";
            default -> level.toUpperCase();
        };
    }

    @Override
    public boolean canParse(String line) {
        return isJson(line);
    }

    private boolean isJson(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }

        String trimmed = line.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    @Override
    public String getParserType() {
        return "JSON";
    }
}
