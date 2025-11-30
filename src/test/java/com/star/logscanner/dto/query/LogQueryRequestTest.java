package com.star.logscanner.dto.query;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LogQueryRequestTest {
    
    private Validator validator;
    
    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {
        
        @Test
        @DisplayName("Should validate with required fields")
        void shouldValidateWithRequiredFields() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .build();
            
            Set<ConstraintViolation<LogQueryRequest>> violations = validator.validate(request);
            
            assertTrue(violations.isEmpty());
        }
        
        @Test
        @DisplayName("Should fail validation when jobId is null")
        void shouldFailWhenJobIdNull() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId(null)
                    .build();
            
            Set<ConstraintViolation<LogQueryRequest>> violations = validator.validate(request);
            
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("jobId")));
        }
        
        @Test
        @DisplayName("Should fail validation when jobId is blank")
        void shouldFailWhenJobIdBlank() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("   ")
                    .build();
            
            Set<ConstraintViolation<LogQueryRequest>> violations = validator.validate(request);
            
            assertFalse(violations.isEmpty());
        }
        
        @Test
        @DisplayName("Should fail validation when page is negative")
        void shouldFailWhenPageNegative() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .page(-1)
                    .build();
            
            Set<ConstraintViolation<LogQueryRequest>> violations = validator.validate(request);
            
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("page")));
        }
        
        @Test
        @DisplayName("Should fail validation when size is zero")
        void shouldFailWhenSizeZero() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .size(0)
                    .build();
            
            Set<ConstraintViolation<LogQueryRequest>> violations = validator.validate(request);
            
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("size")));
        }
        
        @Test
        @DisplayName("Should fail validation when size exceeds 1000")
        void shouldFailWhenSizeExceeds1000() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .size(1001)
                    .build();
            
            Set<ConstraintViolation<LogQueryRequest>> violations = validator.validate(request);
            
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("size")));
        }
    }
    
    @Nested
    @DisplayName("Default Values Tests")
    class DefaultValuesTests {
        
        @Test
        @DisplayName("Should have default page value")
        void shouldHaveDefaultPageValue() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .build();
            
            assertEquals(0, request.getPage());
        }
        
        @Test
        @DisplayName("Should have default size value")
        void shouldHaveDefaultSizeValue() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .build();
            
            assertEquals(50, request.getSize());
        }
        
        @Test
        @DisplayName("Should have default sortBy value")
        void shouldHaveDefaultSortByValue() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .build();
            
            assertEquals("timestamp", request.getSortBy());
        }
        
        @Test
        @DisplayName("Should have default sortDirection value")
        void shouldHaveDefaultSortDirectionValue() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .build();
            
            assertEquals("desc", request.getSortDirection());
        }
        
        @Test
        @DisplayName("Should have default includeSummary value")
        void shouldHaveDefaultIncludeSummaryValue() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .build();
            
            assertTrue(request.getIncludeSummary());
        }
        
        @Test
        @DisplayName("Should have default highlightMatches value")
        void shouldHaveDefaultHighlightMatchesValue() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .build();
            
            assertFalse(request.getHighlightMatches());
        }
    }
    
    @Nested
    @DisplayName("Helper Method Tests")
    class HelperMethodTests {
        
        @Test
        @DisplayName("Should return default search fields when none specified")
        void shouldReturnDefaultSearchFields() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .build();
            
            List<String> fields = request.getEffectiveSearchFields();
            
            assertNotNull(fields);
            assertTrue(fields.contains("message"));
            assertTrue(fields.contains("rawLine"));
            assertTrue(fields.contains("stackTrace"));
        }
        
        @Test
        @DisplayName("Should return custom search fields when specified")
        void shouldReturnCustomSearchFields() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .searchFields(List.of("message", "logger"))
                    .build();
            
            List<String> fields = request.getEffectiveSearchFields();
            
            assertEquals(2, fields.size());
            assertTrue(fields.contains("message"));
            assertTrue(fields.contains("logger"));
        }
        
        @Test
        @DisplayName("Should detect no filters")
        void shouldDetectNoFilters() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .build();
            
            assertFalse(request.hasFilters());
        }
        
        @Test
        @DisplayName("Should detect search text filter")
        void shouldDetectSearchTextFilter() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .searchText("error")
                    .build();
            
            assertTrue(request.hasFilters());
        }
        
        @Test
        @DisplayName("Should detect level filter")
        void shouldDetectLevelFilter() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .levels(List.of("ERROR"))
                    .build();
            
            assertTrue(request.hasFilters());
        }
        
        @Test
        @DisplayName("Should detect date filter")
        void shouldDetectDateFilter() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .startDate(LocalDateTime.now())
                    .build();
            
            assertTrue(request.hasFilters());
        }
        
        @Test
        @DisplayName("Should detect boolean filter")
        void shouldDetectBooleanFilter() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .hasError(true)
                    .build();
            
            assertTrue(request.hasFilters());
        }
        
        @Test
        @DisplayName("Should normalize sort direction to lowercase")
        void shouldNormalizeSortDirection() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .sortDirection("ASC")
                    .build();
            
            assertEquals("asc", request.getEffectiveSortDirection());
        }
        
        @Test
        @DisplayName("Should default to desc for invalid sort direction")
        void shouldDefaultToDescForInvalidSortDirection() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .sortDirection("invalid")
                    .build();
            
            assertEquals("desc", request.getEffectiveSortDirection());
        }
        
        @Test
        @DisplayName("Should default to desc for null sort direction")
        void shouldDefaultToDescForNullSortDirection() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .sortDirection(null)
                    .build();
            
            assertEquals("desc", request.getEffectiveSortDirection());
        }
    }
    
    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {
        
        @Test
        @DisplayName("Should build request with all fields")
        void shouldBuildRequestWithAllFields() {
            LocalDateTime now = LocalDateTime.now();
            
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .searchText("error")
                    .searchFields(List.of("message"))
                    .levels(List.of("ERROR", "WARN"))
                    .fileName("app.log")
                    .logger("com.example")
                    .thread("main")
                    .source("Service")
                    .hostname("server-001")
                    .application("my-app")
                    .environment("prod")
                    .hasError(true)
                    .hasStackTrace(false)
                    .tags(List.of("important"))
                    .startDate(now.minusDays(1))
                    .endDate(now)
                    .minLineNumber(1L)
                    .maxLineNumber(1000L)
                    .sortBy("level")
                    .sortDirection("asc")
                    .page(5)
                    .size(100)
                    .includeFields(List.of("timestamp", "message"))
                    .excludeFields(List.of("rawLine"))
                    .includeSummary(false)
                    .highlightMatches(true)
                    .build();
            
            assertEquals("test-job-123", request.getJobId());
            assertEquals("error", request.getSearchText());
            assertEquals(List.of("ERROR", "WARN"), request.getLevels());
            assertEquals("app.log", request.getFileName());
            assertEquals(true, request.getHasError());
            assertEquals(5, request.getPage());
            assertEquals(100, request.getSize());
            assertEquals(false, request.getIncludeSummary());
            assertEquals(true, request.getHighlightMatches());
        }
    }
}
