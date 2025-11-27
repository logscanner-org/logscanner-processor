package com.star.logscanner.parser;

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
    public LogEntry parseLine(String line, long lineNumber, String timestampFormat) {
        if (!isJson(line)) {
            return null;
        }
        
        try {
            JsonNode jsonNode = objectMapper.readTree(line);
            
            LogEntry entry = LogEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .lineNumber(lineNumber)
                    .rawLine(line)
                    .indexedAt(LocalDateTime.now())
                    .build();
            
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
            
            return entry;
            
        } catch (Exception e) {
            log.debug("Failed to parse JSON log line: {}", e.getMessage());
            return null;
        }
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
        
        switch (level.toUpperCase()) {
            case "WARNING":
            case "WARN":
                return "WARN";
            case "SEVERE":
            case "FATAL":
            case "CRITICAL":
                return "ERROR";
            case "FINE":
            case "FINER":
            case "FINEST":
            case "VERBOSE":
                return "DEBUG";
            case "CONFIG":
            case "NOTICE":
                return "INFO";
            default:
                return level.toUpperCase();
        }
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
