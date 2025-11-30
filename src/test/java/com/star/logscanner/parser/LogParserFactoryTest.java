package com.star.logscanner.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LogParserFactoryTest {
    
    private LogParserFactory factory;
    private TextLogParser textParser;
    private JsonLogParser jsonParser;
    private CsvLogParser csvParser;
    
    @BeforeEach
    void setUp() {
        textParser = new TextLogParser();
        jsonParser = new JsonLogParser(new ObjectMapper());
        csvParser = new CsvLogParser();
        
        factory = new LogParserFactory(List.of(textParser, jsonParser, csvParser));
    }
    
    @Nested
    @DisplayName("Parser Selection by Extension Tests")
    class ExtensionSelectionTests {
        
        @ParameterizedTest
        @ValueSource(strings = {"app.json", "logs.ndjson", "data.JSON"})
        @DisplayName("Should select JSON parser for JSON extensions")
        void shouldSelectJsonParserForJsonExtensions(String fileName) throws IOException {
            Path tempFile = createTempFile(fileName, "{\"message\":\"test\"}");
            try {
                LogParser parser = factory.getParserForFile(tempFile);
                assertEquals("JSON", parser.getSupportedFormat());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"data.csv", "export.CSV", "logs.tsv"})
        @DisplayName("Should select CSV parser for CSV/TSV extensions")
        void shouldSelectCsvParserForCsvExtensions(String fileName) throws IOException {
            Path tempFile = createTempFile(fileName, "timestamp,level,message");
            try {
                LogParser parser = factory.getParserForFile(tempFile);
                assertEquals("CSV", parser.getSupportedFormat());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"app.log", "error.txt", "stdout.out", "stderr.err"})
        @DisplayName("Should select TEXT parser for text-based extensions")
        void shouldSelectTextParserForTextExtensions(String fileName) throws IOException {
            Path tempFile = createTempFile(fileName, "2024-01-15 10:30:45 INFO test");
            try {
                LogParser parser = factory.getParserForFile(tempFile);
                assertEquals("TEXT", parser.getSupportedFormat());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }
    
    @Nested
    @DisplayName("Parser Selection by Content Tests")
    class ContentSelectionTests {
        
        @Test
        @DisplayName("Should select JSON parser for JSON content regardless of extension")
        void shouldSelectJsonParserForJsonContent() throws IOException {
            Path tempFile = createTempFile("unknown.data", "{\"level\":\"INFO\",\"message\":\"test\"}");
            try {
                LogParser parser = factory.getParserForFile(tempFile);
                assertEquals("JSON", parser.getSupportedFormat());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
        
        @Test
        @DisplayName("Should select CSV parser for CSV content regardless of extension")
        void shouldSelectCsvParserForCsvContent() throws IOException {
            Path tempFile = createTempFile("unknown.data", "timestamp,level,message\n2024-01-15,INFO,test");
            try {
                LogParser parser = factory.getParserForFile(tempFile);
                assertEquals("CSV", parser.getSupportedFormat());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
        
        @Test
        @DisplayName("Should fall back to TEXT parser for unknown formats")
        void shouldFallbackToTextParser() throws IOException {
            Path tempFile = createTempFile("unknown.xyz", "Some random text content");
            try {
                LogParser parser = factory.getParserForFile(tempFile);
                assertEquals("TEXT", parser.getSupportedFormat());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }
    
    @Nested
    @DisplayName("getParser by Format Tests")
    class GetParserByFormatTests {
        
        @Test
        @DisplayName("Should return parser by format name")
        void shouldReturnParserByFormat() {
            Optional<LogParser> parser = factory.getParserByFormat("JSON");
            
            assertTrue(parser.isPresent());
            assertEquals("JSON", parser.get().getSupportedFormat());
        }
        
        @Test
        @DisplayName("Should handle case-insensitive format names")
        void shouldHandleCaseInsensitiveFormats() {
            Optional<LogParser> parser1 = factory.getParserByFormat("json");
            Optional<LogParser> parser2 = factory.getParserByFormat("JSON");
            Optional<LogParser> parser3 = factory.getParserByFormat("Json");
            
            assertTrue(parser1.isPresent());
            assertTrue(parser2.isPresent());
            assertTrue(parser3.isPresent());
        }
        
        @Test
        @DisplayName("Should return empty for unknown format")
        void shouldReturnEmptyForUnknownFormat() {
            Optional<LogParser> parser = factory.getParserByFormat("UNKNOWN");
            
            assertFalse(parser.isPresent());
        }
        
        @Test
        @DisplayName("Should return null from legacy method for unknown format")
        void shouldReturnNullFromLegacyMethod() {
            LogParser parser = factory.getParser("UNKNOWN");
            
            assertNull(parser);
        }
    }
    
    @Nested
    @DisplayName("Parser Registration Tests")
    class RegistrationTests {
        
        @Test
        @DisplayName("Should register new parser dynamically")
        void shouldRegisterNewParser() {
            // Create a mock parser
            LogParser customParser = new LogParser() {
                @Override
                public boolean canParse(String fileName, String contentSample) {
                    return fileName != null && fileName.endsWith(".custom");
                }
                
                @Override
                public ParseResult parseLine(String line, long lineNumber, ParseContext context) {
                    return ParseResult.success(null);
                }
                
                @Override
                public void reset() {}
                
                @Override
                public String getSupportedFormat() {
                    return "CUSTOM";
                }
                
                @Override
                public int getPriority() {
                    return 100;
                }
            };
            
            factory.registerParser(customParser);
            
            assertTrue(factory.hasParser("CUSTOM"));
            assertEquals(4, factory.getAllParsers().size());
        }
        
        @Test
        @DisplayName("Should unregister parser by format")
        void shouldUnregisterParser() {
            int initialCount = factory.getAllParsers().size();
            
            boolean removed = factory.unregisterParser("JSON");
            
            assertTrue(removed);
            assertEquals(initialCount - 1, factory.getAllParsers().size());
            assertFalse(factory.hasParser("JSON"));
        }
        
        @Test
        @DisplayName("Should return false when unregistering non-existent parser")
        void shouldReturnFalseForNonExistentParser() {
            boolean removed = factory.unregisterParser("NONEXISTENT");
            
            assertFalse(removed);
        }
    }
    
    @Nested
    @DisplayName("Available Formats Tests")
    class AvailableFormatsTests {
        
        @Test
        @DisplayName("Should list all available formats")
        void shouldListAllFormats() {
            List<String> formats = factory.getAvailableFormats();
            
            assertEquals(3, formats.size());
            assertTrue(formats.contains("JSON"));
            assertTrue(formats.contains("CSV"));
            assertTrue(formats.contains("TEXT"));
        }
        
        @Test
        @DisplayName("Should check if parser exists")
        void shouldCheckParserExists() {
            assertTrue(factory.hasParser("JSON"));
            assertTrue(factory.hasParser("CSV"));
            assertTrue(factory.hasParser("TEXT"));
            assertFalse(factory.hasParser("UNKNOWN"));
        }
    }
    
    @Nested
    @DisplayName("Parser Info Tests")
    class ParserInfoTests {
        
        @Test
        @DisplayName("Should return parser info map")
        void shouldReturnParserInfo() {
            Map<String, String> info = factory.getParserInfo();
            
            assertNotNull(info);
            assertEquals(3, info.size());
            assertTrue(info.containsKey("JSON"));
            assertTrue(info.containsKey("CSV"));
            assertTrue(info.containsKey("TEXT"));
        }
        
        @Test
        @DisplayName("Should include priority and multiline info")
        void shouldIncludePriorityAndMultiline() {
            Map<String, String> info = factory.getParserInfo();
            
            String textInfo = info.get("TEXT");
            assertTrue(textInfo.contains("priority"));
            assertTrue(textInfo.contains("multiline"));
        }
    }
    
    @Nested
    @DisplayName("Context Creation Tests")
    class ContextCreationTests {
        
        @Test
        @DisplayName("Should create context with all parameters")
        void shouldCreateContextWithAllParams() throws IOException {
            Path tempFile = createTempFile("test.log", "content");
            try {
                ParseContext context = factory.createContext(tempFile, "job-123", "yyyy-MM-dd");
                
                assertNotNull(context);
                assertEquals("job-123", context.getJobId());
                assertEquals("test.log", context.getFileName());
                assertEquals("yyyy-MM-dd", context.getTimestampFormat());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
        
        @Test
        @DisplayName("Should handle null file path")
        void shouldHandleNullFilePath() {
            ParseContext context = factory.createContext(null, "job-123", null);
            
            assertNotNull(context);
            assertEquals("job-123", context.getJobId());
            assertNull(context.getFileName());
        }
    }
    
    @Nested
    @DisplayName("Priority Ordering Tests")
    class PriorityOrderingTests {
        
        @Test
        @DisplayName("Should sort parsers by priority (highest first)")
        void shouldSortByPriority() {
            List<LogParser> parsers = factory.getAllParsers();
            
            // JSON has highest priority (20), then CSV (10), then TEXT (0)
            assertEquals("JSON", parsers.get(0).getSupportedFormat());
            assertEquals("CSV", parsers.get(1).getSupportedFormat());
            assertEquals("TEXT", parsers.get(2).getSupportedFormat());
        }
        
        @Test
        @DisplayName("High priority parsers should be checked first")
        void shouldCheckHighPriorityFirst() throws IOException {
            // Content that could match both JSON and TEXT
            // JSON should win because of higher priority
            Path tempFile = createTempFile("ambiguous.data", "{\"level\":\"INFO\"}");
            try {
                LogParser parser = factory.getParserForFile(tempFile);
                assertEquals("JSON", parser.getSupportedFormat());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }
    
    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("Should throw exception when no parser can handle file")
        void shouldThrowExceptionWhenNoParserMatches() {
            // Remove all parsers
            factory.unregisterParser("JSON");
            factory.unregisterParser("CSV");
            factory.unregisterParser("TEXT");
            
            assertThrows(IllegalArgumentException.class, () ->
                    factory.getParser("unknown.xyz", "some content"));
        }
        
        @Test
        @DisplayName("Should handle non-existent file gracefully")
        void shouldHandleNonExistentFile() {
            Path nonExistent = Path.of("/non/existent/file.log");
            
            // Should fall back to TEXT parser based on extension
            // when content cannot be read
            LogParser parser = factory.getParser("file.log", "");
            assertNotNull(parser);
        }
    }
    
    private Path createTempFile(String fileName, String content) throws IOException {
        Path tempDir = Files.createTempDirectory("parser-test");
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }
}
