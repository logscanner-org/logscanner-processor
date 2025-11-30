package com.star.logscanner.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.star.logscanner.entity.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class JsonLogParserTest {
    
    private JsonLogParser parser;
    private ParseContext context;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        parser = new JsonLogParser(objectMapper);
        context = new ParseContext();
        context.setJobId("test-job-123");
        context.setFileName("test.json");
    }
    
    @Nested
    @DisplayName("Standard JSON Log Format Tests")
    class StandardJsonFormatTests {
        
        @Test
        @DisplayName("Should parse simple JSON log entry")
        void shouldParseSimpleJsonLog() {
            String json = "{\"timestamp\":\"2024-01-15T10:30:45.123\",\"level\":\"INFO\",\"message\":\"Application started\"}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertNotNull(entry);
            assertEquals("INFO", entry.getLevel());
            assertEquals("Application started", entry.getMessage());
        }
        
        @Test
        @DisplayName("Should parse Logstash/ELK format")
        void shouldParseLogstashFormat() {
            String json = "{\"@timestamp\":\"2024-01-15T10:30:45.123Z\",\"level\":\"ERROR\",\"message\":\"Connection failed\",\"host\":\"server1\"}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertEquals("ERROR", entry.getLevel());
            assertEquals("server1", entry.getHostname());
            assertTrue(entry.getHasError());
        }
        
        @Test
        @DisplayName("Should parse Bunyan format")
        void shouldParseBunyanFormat() {
            String json = "{\"name\":\"myapp\",\"hostname\":\"server1\",\"pid\":12345,\"level\":30,\"msg\":\"Request processed\",\"time\":\"2024-01-15T10:30:45.123Z\"}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertEquals("Request processed", entry.getMessage());
            assertEquals("server1", entry.getHostname());
        }
        
        @Test
        @DisplayName("Should parse Winston format")
        void shouldParseWinstonFormat() {
            String json = "{\"level\":\"info\",\"message\":\"User logged in\",\"timestamp\":\"2024-01-15 10:30:45\",\"service\":\"auth-service\"}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertEquals("INFO", entry.getLevel());
            assertEquals("auth-service", entry.getApplication());
        }
    }
    
    @Nested
    @DisplayName("Timestamp Parsing Tests")
    class TimestampParsingTests {
        
        @Test
        @DisplayName("Should parse ISO timestamp")
        void shouldParseIsoTimestamp() {
            String json = "{\"timestamp\":\"2024-01-15T10:30:45.123Z\",\"message\":\"test\"}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            assertNotNull(result.getEntry().getTimestamp());
        }
        
        @Test
        @DisplayName("Should parse epoch milliseconds timestamp")
        void shouldParseEpochMillisTimestamp() {
            String json = "{\"timestamp\":1705315845123,\"message\":\"test\"}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            assertNotNull(result.getEntry().getTimestamp());
        }
        
        @Test
        @DisplayName("Should handle various timestamp field names")
        void shouldHandleVariousTimestampFields() {
            String[] timestampFields = {"timestamp", "time", "@timestamp", "datetime", "ts"};
            
            for (String field : timestampFields) {
                String json = String.format("{\"%s\":\"2024-01-15T10:30:45.123\",\"message\":\"test\"}", field);
                
                ParseResult result = parser.parseLine(json, 1, context);
                
                assertTrue(result.isSuccess(), "Failed for field: " + field);
                assertNotNull(result.getEntry().getTimestamp(), "Timestamp null for field: " + field);
            }
        }
    }
    
    @Nested
    @DisplayName("Level Normalization Tests")
    class LevelNormalizationTests {
        
        @ParameterizedTest
        @ValueSource(strings = {"ERROR", "error", "Error"})
        @DisplayName("Should parse ERROR level case-insensitively")
        void shouldParseErrorLevel(String level) {
            String json = String.format("{\"level\":\"%s\",\"message\":\"test\"}", level);
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            assertEquals("ERROR", result.getEntry().getLevel());
        }
        
        @Test
        @DisplayName("Should normalize WARN/WARNING to WARN")
        void shouldNormalizeWarnLevel() {
            String json = "{\"level\":\"WARNING\",\"message\":\"test\"}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            assertEquals("WARN", result.getEntry().getLevel());
        }
        
        @Test
        @DisplayName("Should normalize FATAL/CRITICAL to ERROR")
        void shouldNormalizeFatalLevel() {
            String[] levels = {"FATAL", "CRITICAL"};
            
            for (String level : levels) {
                String json = String.format("{\"level\":\"%s\",\"message\":\"test\"}", level);
                
                ParseResult result = parser.parseLine(json, 1, context);
                
                assertTrue(result.isSuccess());
                assertEquals("ERROR", result.getEntry().getLevel());
                assertTrue(result.getEntry().getHasError());
            }
        }
    }
    
    @Nested
    @DisplayName("Field Extraction Tests")
    class FieldExtractionTests {
        
        @Test
        @DisplayName("Should extract logger field")
        void shouldExtractLogger() {
            String json = "{\"logger\":\"com.example.MyClass\",\"message\":\"test\"}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            assertEquals("com.example.MyClass", result.getEntry().getLogger());
            assertEquals("MyClass", result.getEntry().getSource());
        }
        
        @Test
        @DisplayName("Should extract thread field")
        void shouldExtractThread() {
            String json = "{\"thread\":\"main\",\"message\":\"test\"}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            assertEquals("main", result.getEntry().getThread());
        }
        
        @Test
        @DisplayName("Should extract stack trace")
        void shouldExtractStackTrace() {
            String json = "{\"level\":\"ERROR\",\"message\":\"Error occurred\",\"stack_trace\":\"java.lang.NullPointerException\\n\\tat com.example.Test.method(Test.java:42)\"}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertTrue(entry.getHasStackTrace());
            assertNotNull(entry.getStackTrace());
        }
        
        @Test
        @DisplayName("Should extract hostname")
        void shouldExtractHostname() {
            String json = "{\"hostname\":\"server-001\",\"message\":\"test\"}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            assertEquals("server-001", result.getEntry().getHostname());
        }
        
        @Test
        @DisplayName("Should extract application")
        void shouldExtractApplication() {
            String json = "{\"application\":\"my-service\",\"message\":\"test\"}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            assertEquals("my-service", result.getEntry().getApplication());
        }
        
        @Test
        @DisplayName("Should extract environment")
        void shouldExtractEnvironment() {
            String json = "{\"environment\":\"production\",\"message\":\"test\"}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            assertEquals("production", result.getEntry().getEnvironment());
        }
    }
    
    @Nested
    @DisplayName("Metadata Extraction Tests")
    class MetadataExtractionTests {
        
        @Test
        @DisplayName("Should extract custom fields as metadata")
        void shouldExtractCustomFields() {
            String json = "{\"level\":\"INFO\",\"message\":\"test\",\"userId\":123,\"requestId\":\"abc-123\",\"duration\":150.5}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertNotNull(entry.getMetadata());
            assertEquals(123, entry.getMetadata().get("userId"));
            assertEquals("abc-123", entry.getMetadata().get("requestId"));
            assertEquals(150.5, entry.getMetadata().get("duration"));
        }
        
        @Test
        @DisplayName("Should handle nested JSON in metadata")
        void shouldHandleNestedJson() {
            String json = "{\"message\":\"test\",\"context\":{\"user\":\"admin\",\"ip\":\"192.168.1.1\"}}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertNotNull(entry.getMetadata());
            assertNotNull(entry.getMetadata().get("context"));
        }
        
        @Test
        @DisplayName("Should handle arrays in metadata")
        void shouldHandleArrays() {
            String json = "{\"message\":\"test\",\"tags\":[\"important\",\"production\"]}";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertNotNull(entry.getMetadata());
            assertNotNull(entry.getMetadata().get("tags"));
        }
    }
    
    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJson() {
            String json = "{\"level\":\"INFO\",\"message\":";
            
            ParseResult result = parser.parseLine(json, 1, context);
            
            assertTrue(result.isFailed());
            assertNotNull(result.getErrorMessage());
        }
        
        @Test
        @DisplayName("Should handle null line")
        void shouldHandleNullLine() {
            ParseResult result = parser.parseLine(null, 1, context);
            
            assertTrue(result.isSkipped());
        }
        
        @Test
        @DisplayName("Should handle empty line")
        void shouldHandleEmptyLine() {
            ParseResult result = parser.parseLine("", 1, context);
            
            assertTrue(result.isSkipped());
        }
        
        @Test
        @DisplayName("Should handle non-JSON text")
        void shouldHandleNonJsonText() {
            String text = "This is not JSON";
            
            ParseResult result = parser.parseLine(text, 1, context);
            
            assertTrue(result.isFailed());
        }
        
        @Test
        @DisplayName("Should handle JSON array at top level")
        void shouldHandleJsonArray() {
            String json = "[{\"message\":\"test1\"},{\"message\":\"test2\"}]";
            
            // Top-level arrays are valid JSON but not valid for NDJSON parsing
            ParseResult result = parser.parseLine(json, 1, context);
            
            // Should attempt to parse or fail gracefully
            assertNotNull(result);
        }
    }
    
    @Nested
    @DisplayName("canParse Detection Tests")
    class CanParseTests {
        
        @ParameterizedTest
        @ValueSource(strings = {"test.json", "logs.ndjson", "data.JSON"})
        @DisplayName("Should accept JSON file extensions")
        void shouldAcceptJsonExtensions(String fileName) {
            assertTrue(parser.canParse(fileName, "{\"message\":\"test\"}"));
        }
        
        @Test
        @DisplayName("Should accept JSON content regardless of filename")
        void shouldAcceptJsonContent() {
            assertTrue(parser.canParse("test.log", "{\"message\":\"test\"}"));
        }
        
        @Test
        @DisplayName("Should reject non-JSON content")
        void shouldRejectNonJsonContent() {
            assertFalse(parser.canParse(null, "2024-01-15 INFO test message"));
        }
        
        @Test
        @DisplayName("Should detect JSON starting with object")
        void shouldDetectJsonObject() {
            assertTrue(parser.canParse(null, "{\"key\":\"value\"}"));
        }
        
        @Test
        @DisplayName("Should detect JSON starting with array")
        void shouldDetectJsonArray() {
            assertTrue(parser.canParse(null, "[{\"key\":\"value\"}]"));
        }
    }
    
    @Nested
    @DisplayName("Parser Configuration Tests")
    class ConfigurationTests {
        
        @Test
        @DisplayName("Should report correct supported format")
        void shouldReportCorrectFormat() {
            assertEquals("JSON", parser.getSupportedFormat());
        }
        
        @Test
        @DisplayName("Should have high priority")
        void shouldHaveHighPriority() {
            assertTrue(parser.getPriority() > 0);
        }
        
        @Test
        @DisplayName("Should not support multi-line")
        void shouldNotSupportMultiLine() {
            assertFalse(parser.supportsMultiLine());
        }
        
        @Test
        @DisplayName("Reset should be safe to call")
        void resetShouldBeSafe() {
            assertDoesNotThrow(() -> parser.reset());
        }
    }
}
