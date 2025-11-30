package com.star.logscanner.parser;

import com.star.logscanner.entity.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TextLogParserTest {
    
    private TextLogParser parser;
    private ParseContext context;
    
    @BeforeEach
    void setUp() {
        parser = new TextLogParser();
        context = new ParseContext();
        context.setJobId("test-job-123");
        context.setFileName("test.log");
    }
    
    @Nested
    @DisplayName("Log4j/Logback Format Tests")
    class Log4jFormatTests {
        
        @Test
        @DisplayName("Should parse standard Log4j format")
        void shouldParseStandardLog4jFormat() {
            String line = "2024-01-15 10:30:45.123 [main] ERROR com.example.MyClass - An error occurred";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertNotNull(entry);
            assertEquals("ERROR", entry.getLevel());
            assertEquals("com.example.MyClass", entry.getLogger());
            assertEquals("main", entry.getThread());
            assertEquals("An error occurred", entry.getMessage());
            assertTrue(entry.getHasError());
        }
        
        @Test
        @DisplayName("Should parse Log4j format with milliseconds comma separator")
        void shouldParseLog4jWithCommaSeparator() {
            String line = "2024-01-15 10:30:45,123 [http-nio-8080-exec-1] INFO com.example.Service - Request processed";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertEquals("INFO", entry.getLevel());
            assertEquals("http-nio-8080-exec-1", entry.getThread());
            assertFalse(entry.getHasError());
        }
        
        @Test
        @DisplayName("Should parse Spring Boot format")
        void shouldParseSpringBootFormat() {
            String line = "2024-01-15 10:30:45.123  INFO 12345 --- [main] c.e.Application : Application started";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertEquals("INFO", entry.getLevel());
            assertEquals("main", entry.getThread());
        }
    }
    
    @Nested
    @DisplayName("Apache/Nginx Format Tests")
    class ApacheFormatTests {
        
        @Test
        @DisplayName("Should parse Apache Combined Log Format")
        void shouldParseApacheCombinedFormat() {
            String line = "192.168.1.100 - admin [15/Jan/2024:10:30:45 +0000] \"GET /api/users HTTP/1.1\" 200 1234 \"https://example.com\" \"Mozilla/5.0\"";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertNotNull(entry.getMetadata());
            assertEquals("192.168.1.100", entry.getMetadata().get("client_ip"));
            assertEquals(200, entry.getMetadata().get("http_status"));
        }
        
        @Test
        @DisplayName("Should parse Apache error log")
        void shouldParseApacheErrorResponse() {
            String line = "192.168.1.100 - - [15/Jan/2024:10:30:45 +0000] \"POST /api/submit HTTP/1.1\" 500 0";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertEquals("ERROR", entry.getLevel());
            assertTrue(entry.getHasError());
        }
    }
    
    @Nested
    @DisplayName("Syslog Format Tests")
    class SyslogFormatTests {
        
        @Test
        @DisplayName("Should parse Syslog format with PID")
        void shouldParseSyslogWithPid() {
            String line = "Jan 15 10:30:45 myserver sshd[12345]: Connection from 192.168.1.1";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertEquals("myserver", entry.getHostname());
            assertNotNull(entry.getMetadata());
        }
        
        @Test
        @DisplayName("Should parse Syslog format without PID")
        void shouldParseSyslogWithoutPid() {
            String line = "Jan 15 10:30:45 webserver nginx: 200 GET /index.html";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
        }
    }
    
    @Nested
    @DisplayName("Multi-line Stack Trace Tests")
    class StackTraceTests {
        
        @Test
        @DisplayName("Should detect exception line")
        void shouldDetectExceptionLine() {
            String line = "java.lang.NullPointerException: Object is null";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess() || result.isBuffered());
        }
        
        @Test
        @DisplayName("Should detect stack trace continuation")
        void shouldDetectStackTraceContinuation() {
            // First, parse an error line
            String errorLine = "2024-01-15 10:30:45.123 [main] ERROR com.example.MyClass - NullPointerException";
            parser.parseLine(errorLine, 1, context);
            
            // Then parse a stack trace line
            String stackLine = "\tat com.example.MyClass.method(MyClass.java:42)";
            
            ParseResult result = parser.parseLine(stackLine, 2, context);
            
            assertTrue(result.isContinuation());
        }
        
        @Test
        @DisplayName("Should handle Caused by lines")
        void shouldHandleCausedByLines() {
            String line = "Caused by: java.io.IOException: File not found";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            // Should either be successful (standalone) or continuation
            assertNotNull(result);
        }
        
        @Test
        @DisplayName("Should flush pending multi-line entry")
        void shouldFlushPendingEntry() {
            String errorLine = "2024-01-15 10:30:45.123 [main] ERROR com.example.MyClass - Error with stack trace";
            parser.parseLine(errorLine, 1, context);
            
            String stackLine = "\tat com.example.MyClass.method(MyClass.java:42)";
            parser.parseLine(stackLine, 2, context);
            
            LogEntry pending = parser.flushPending();
            
            assertNotNull(pending);
            assertTrue(pending.getHasStackTrace());
            assertNotNull(pending.getStackTrace());
        }
    }
    
    @Nested
    @DisplayName("Metadata Extraction Tests")
    class MetadataExtractionTests {
        
        @Test
        @DisplayName("Should extract key-value pairs")
        void shouldExtractKeyValuePairs() {
            String line = "2024-01-15 10:30:45.123 [main] INFO - Request processed userId=123 action=login";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertNotNull(entry.getMetadata());
            assertEquals("123", entry.getMetadata().get("userId"));
            assertEquals("login", entry.getMetadata().get("action"));
        }
        
        @Test
        @DisplayName("Should extract IP addresses")
        void shouldExtractIpAddresses() {
            String line = "2024-01-15 10:30:45.123 [main] INFO - Connection from 192.168.1.100";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertNotNull(entry.getMetadata());
            assertEquals("192.168.1.100", entry.getMetadata().get("ip_address"));
        }
        
        @Test
        @DisplayName("Should extract URLs")
        void shouldExtractUrls() {
            String line = "2024-01-15 10:30:45.123 [main] INFO - Fetching https://api.example.com/data";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertNotNull(entry.getMetadata());
            assertEquals("https://api.example.com/data", entry.getMetadata().get("url"));
        }
        
        @Test
        @DisplayName("Should extract request IDs")
        void shouldExtractRequestIds() {
            String line = "2024-01-15 10:30:45.123 [main] INFO - Processing request_id=abc-123-def";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertNotNull(entry.getMetadata());
            assertEquals("abc-123-def", entry.getMetadata().get("request_id"));
        }
    }
    
    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {
        
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
        @DisplayName("Should handle whitespace-only line")
        void shouldHandleWhitespaceOnlyLine() {
            ParseResult result = parser.parseLine("   \t  ", 1, context);
            
            assertTrue(result.isSkipped());
        }
        
        @Test
        @DisplayName("Should handle very long lines")
        void shouldHandleVeryLongLines() {
            String longMessage = "A".repeat(10000);
            String line = "2024-01-15 10:30:45.123 [main] INFO - " + longMessage;
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
        }
        
        @Test
        @DisplayName("Should create basic entry for unrecognized format")
        void shouldCreateBasicEntryForUnrecognizedFormat() {
            String line = "Some random log message without clear format";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
            LogEntry entry = result.getEntry();
            assertEquals(line, entry.getMessage());
        }
    }
    
    @Nested
    @DisplayName("Parser Configuration Tests")
    class ConfigurationTests {
        
        @Test
        @DisplayName("Should use custom timestamp format from context")
        void shouldUseCustomTimestampFormat() {
            context.setTimestampFormat("dd/MM/yyyy HH:mm:ss");
            String line = "[15/01/2024 10:30:45] INFO test message";
            
            ParseResult result = parser.parseLine(line, 1, context);
            
            assertTrue(result.isSuccess());
            assertNotNull(result.getEntry().getTimestamp());
        }
        
        @Test
        @DisplayName("Should reset parser state")
        void shouldResetParserState() {
            // Parse an error with potential multi-line
            parser.parseLine("2024-01-15 10:30:45.123 [main] ERROR - Exception", 1, context);
            
            // Reset
            parser.reset();
            
            // Should not have any pending entries
            assertNull(parser.flushPending());
        }
        
        @Test
        @DisplayName("Should report correct supported format")
        void shouldReportCorrectSupportedFormat() {
            assertEquals("TEXT", parser.getSupportedFormat());
        }
        
        @Test
        @DisplayName("Should support multi-line parsing")
        void shouldSupportMultiLineParsing() {
            assertTrue(parser.supportsMultiLine());
        }
    }
    
    @Nested
    @DisplayName("canParse Detection Tests")
    class CanParseTests {
        
        @ParameterizedTest
        @ValueSource(strings = {
            "test.log",
            "application.log",
            "error.txt",
            "stdout.out",
            "stderr.err"
        })
        @DisplayName("Should accept text-based file extensions")
        void shouldAcceptTextBasedExtensions(String fileName) {
            assertTrue(parser.canParse(fileName, "2024-01-15 10:30:45 INFO test"));
        }
        
        @Test
        @DisplayName("Should reject JSON content")
        void shouldRejectJsonContent() {
            assertFalse(parser.canParse("test.log", "{\"level\":\"INFO\"}"));
        }
        
        @Test
        @DisplayName("Should accept valid log content")
        void shouldAcceptValidLogContent() {
            assertTrue(parser.canParse(null, "2024-01-15 10:30:45.123 [main] INFO - message"));
        }
    }
}
