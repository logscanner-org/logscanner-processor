package com.star.logscanner.query;

import com.star.logscanner.dto.query.LogQueryRequest;
import com.star.logscanner.dto.query.TimelineData;
import com.star.logscanner.exception.InvalidQueryException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogQueryBuilderTest {
    
    private LogQueryBuilder queryBuilder;
    
    @BeforeEach
    void setUp() {
        queryBuilder = new LogQueryBuilder();
    }
    
    @Nested
    @DisplayName("Basic Query Building Tests")
    class BasicQueryTests {
        
        @Test
        @DisplayName("Should build basic query with only jobId")
        void shouldBuildBasicQuery() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
            assertNotNull(query.getQuery());
        }
        
        @Test
        @DisplayName("Should build query with search text")
        void shouldBuildQueryWithSearchText() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .searchText("NullPointerException")
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
        }
        
        @Test
        @DisplayName("Should build query with custom search fields")
        void shouldBuildQueryWithCustomSearchFields() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .searchText("error")
                    .searchFields(List.of("message", "stackTrace"))
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
        }
    }
    
    @Nested
    @DisplayName("Filter Tests")
    class FilterTests {
        
        @Test
        @DisplayName("Should build query with level filter")
        void shouldBuildQueryWithLevelFilter() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .levels(List.of("ERROR", "WARN"))
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
        }
        
        @Test
        @DisplayName("Should build query with multiple keyword filters")
        void shouldBuildQueryWithKeywordFilters() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .fileName("app.log")
                    .logger("com.example.Service")
                    .thread("main")
                    .hostname("server-001")
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
        }
        
        @Test
        @DisplayName("Should build query with boolean filters")
        void shouldBuildQueryWithBooleanFilters() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .hasError(true)
                    .hasStackTrace(true)
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
        }
        
        @Test
        @DisplayName("Should build query with wildcard filter")
        void shouldBuildQueryWithWildcardFilter() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .logger("com.example.*")
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
        }
        
        @Test
        @DisplayName("Should build query with tags filter")
        void shouldBuildQueryWithTagsFilter() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .tags(List.of("important", "reviewed"))
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
        }
    }
    
    @Nested
    @DisplayName("Date Range Tests")
    class DateRangeTests {
        
        @Test
        @DisplayName("Should build query with start date only")
        void shouldBuildQueryWithStartDateOnly() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .startDate(LocalDateTime.of(2024, 1, 15, 0, 0))
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
        }
        
        @Test
        @DisplayName("Should build query with end date only")
        void shouldBuildQueryWithEndDateOnly() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .endDate(LocalDateTime.of(2024, 1, 15, 23, 59, 59))
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
        }
        
        @Test
        @DisplayName("Should build query with full date range")
        void shouldBuildQueryWithFullDateRange() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .startDate(LocalDateTime.of(2024, 1, 15, 0, 0))
                    .endDate(LocalDateTime.of(2024, 1, 15, 23, 59, 59))
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
        }
        
        @Test
        @DisplayName("Should reject invalid date range")
        void shouldRejectInvalidDateRange() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .startDate(LocalDateTime.of(2024, 1, 15, 23, 59))
                    .endDate(LocalDateTime.of(2024, 1, 15, 0, 0))
                    .build();
            
            assertThrows(InvalidQueryException.class, () ->
                    queryBuilder.buildSearchQuery(request));
        }
    }
    
    @Nested
    @DisplayName("Line Number Range Tests")
    class LineNumberRangeTests {
        
        @Test
        @DisplayName("Should build query with line number range")
        void shouldBuildQueryWithLineNumberRange() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .minLineNumber(100L)
                    .maxLineNumber(200L)
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
        }
        
        @Test
        @DisplayName("Should reject invalid line number range")
        void shouldRejectInvalidLineNumberRange() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .minLineNumber(200L)
                    .maxLineNumber(100L)
                    .build();
            
            assertThrows(InvalidQueryException.class, () ->
                    queryBuilder.buildSearchQuery(request));
        }
    }
    
    @Nested
    @DisplayName("Sorting Tests")
    class SortingTests {
        
        @ParameterizedTest
        @ValueSource(strings = {"timestamp", "lineNumber", "level", "logger", "thread"})
        @DisplayName("Should build query with valid sort fields")
        void shouldBuildQueryWithValidSortFields(String sortField) {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .sortBy(sortField)
                    .sortDirection("asc")
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
        }
        
        @Test
        @DisplayName("Should reject invalid sort field")
        void shouldRejectInvalidSortField() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .sortBy("invalidField")
                    .build();
            
            assertThrows(InvalidQueryException.class, () ->
                    queryBuilder.buildSearchQuery(request));
        }
        
        @Test
        @DisplayName("Should handle both sort directions")
        void shouldHandleBothSortDirections() {
            LogQueryRequest ascRequest = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .sortDirection("asc")
                    .build();
            
            LogQueryRequest descRequest = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .sortDirection("desc")
                    .build();
            
            assertNotNull(queryBuilder.buildSearchQuery(ascRequest));
            assertNotNull(queryBuilder.buildSearchQuery(descRequest));
        }
    }
    
    @Nested
    @DisplayName("Pagination Tests")
    class PaginationTests {
        
        @Test
        @DisplayName("Should use default pagination")
        void shouldUseDefaultPagination() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query.getPageable());
            assertEquals(0, query.getPageable().getPageNumber());
            assertEquals(50, query.getPageable().getPageSize());
        }
        
        @Test
        @DisplayName("Should use custom pagination")
        void shouldUseCustomPagination() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .page(5)
                    .size(100)
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query.getPageable());
            assertEquals(5, query.getPageable().getPageNumber());
            assertEquals(100, query.getPageable().getPageSize());
        }
    }
    
    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {
        
        @Test
        @DisplayName("Should reject null jobId")
        void shouldRejectNullJobId() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId(null)
                    .build();
            
            assertThrows(InvalidQueryException.class, () ->
                    queryBuilder.buildSearchQuery(request));
        }
        
        @Test
        @DisplayName("Should reject blank jobId")
        void shouldRejectBlankJobId() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("   ")
                    .build();
            
            assertThrows(InvalidQueryException.class, () ->
                    queryBuilder.buildSearchQuery(request));
        }
    }
    
    @Nested
    @DisplayName("Aggregation Query Tests")
    class AggregationQueryTests {
        
        @Test
        @DisplayName("Should build count query")
        void shouldBuildCountQuery() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .levels(List.of("ERROR"))
                    .build();
            
            NativeQuery query = queryBuilder.buildCountQuery(request);
            
            assertNotNull(query);
        }
    }
    
    @Nested
    @DisplayName("Timeline Query Tests")
    class TimelineQueryTests {
        
        @ParameterizedTest
        @ValueSource(strings = {"1m", "5m", "15m", "30m", "1h", "1d", "1w"})
        @DisplayName("Should build timeline query with valid intervals")
        void shouldBuildTimelineQueryWithValidIntervals(String interval) {
            TimelineData.Interval parsedInterval = TimelineData.Interval.parse(interval);
            
            NativeQuery query = queryBuilder.buildTimelineQuery("test-job-123", parsedInterval);
            
            assertNotNull(query);
        }
    }
    
    @Nested
    @DisplayName("Unique Values Query Tests")
    class UniqueValuesQueryTests {
        
        @ParameterizedTest
        @ValueSource(strings = {"level", "logger", "thread", "source", "hostname"})
        @DisplayName("Should build unique values query for valid fields")
        void shouldBuildUniqueValuesQueryForValidFields(String field) {
            NativeQuery query = queryBuilder.buildUniqueValuesQuery("test-job-123", field, 100);
            
            assertNotNull(query);
        }
        
        @Test
        @DisplayName("Should reject unsupported field for unique values")
        void shouldRejectUnsupportedField() {
            assertThrows(InvalidQueryException.class, () ->
                    queryBuilder.buildUniqueValuesQuery("test-job-123", "message", 100));
        }
    }
    
    @Nested
    @DisplayName("Field Information Tests")
    class FieldInfoTests {
        
        @Test
        @DisplayName("Should return sortable fields")
        void shouldReturnSortableFields() {
            var fields = queryBuilder.getSortableFields();
            
            assertNotNull(fields);
            assertTrue(fields.contains("timestamp"));
            assertTrue(fields.contains("lineNumber"));
            assertTrue(fields.contains("level"));
        }
        
        @Test
        @DisplayName("Should return keyword fields")
        void shouldReturnKeywordFields() {
            var fields = queryBuilder.getKeywordFields();
            
            assertNotNull(fields);
            assertTrue(fields.contains("jobId"));
            assertTrue(fields.contains("level"));
            assertTrue(fields.contains("logger"));
        }
        
        @Test
        @DisplayName("Should return text fields")
        void shouldReturnTextFields() {
            var fields = queryBuilder.getTextFields();
            
            assertNotNull(fields);
            assertTrue(fields.contains("message"));
            assertTrue(fields.contains("rawLine"));
            assertTrue(fields.contains("stackTrace"));
        }
    }
    
    @Nested
    @DisplayName("Complex Query Tests")
    class ComplexQueryTests {
        
        @Test
        @DisplayName("Should build complex query with all filters")
        void shouldBuildComplexQueryWithAllFilters() {
            LogQueryRequest request = LogQueryRequest.builder()
                    .jobId("test-job-123")
                    .searchText("exception")
                    .searchFields(List.of("message", "stackTrace"))
                    .levels(List.of("ERROR", "WARN"))
                    .fileName("app.log")
                    .logger("com.example.*")
                    .thread("main")
                    .source("UserService")
                    .hostname("server-001")
                    .application("my-app")
                    .environment("production")
                    .hasError(true)
                    .hasStackTrace(true)
                    .tags(List.of("critical"))
                    .startDate(LocalDateTime.of(2024, 1, 15, 0, 0))
                    .endDate(LocalDateTime.of(2024, 1, 15, 23, 59, 59))
                    .minLineNumber(1L)
                    .maxLineNumber(10000L)
                    .sortBy("timestamp")
                    .sortDirection("desc")
                    .page(0)
                    .size(100)
                    .includeSummary(true)
                    .highlightMatches(true)
                    .build();
            
            NativeQuery query = queryBuilder.buildSearchQuery(request);
            
            assertNotNull(query);
            assertNotNull(query.getQuery());
            assertNotNull(query.getPageable());
            assertEquals(100, query.getPageable().getPageSize());
        }
    }
}
