package com.star.logscanner.dto.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.star.logscanner.entity.LogEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for log search queries.
 * 
 * <p>Contains the matching log entries, pagination metadata,
 * and optional filter summary with aggregations.
 * 
 * @author Eshmamatov Obidjon
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Log search query response")
public class LogQueryResponse {

    @Schema(description = "Matching log entries")
    private List<LogEntry> logs;

    @Schema(description = "Pagination information")
    private PaginationInfo pagination;

    @Schema(description = "Filter result summary")
    private FilterSummary summary;

    @Schema(description = "Query execution time in ms")
    private Long queryTimeMs;

    @Schema(description = "Highlighted search matches")
    private Map<String, Map<String, List<String>>> highlights;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Pagination information")
    public static class PaginationInfo {

        @Schema(description = "Current page number", example = "0")
        private int currentPage;

        @Schema(description = "Page size", example = "50")
        private int pageSize;

        @Schema(description = "Total matching elements", example = "1234")
        private long totalElements;

        @Schema(description = "Total pages", example = "25")
        private int totalPages;

        @Schema(description = "Has next page")
        private boolean hasNext;

        @Schema(description = "Has previous page")
        private boolean hasPrevious;

        @Schema(description = "First item index on page")
        private long firstElement;

        @Schema(description = "Last item index on page")
        private long lastElement;

        public static PaginationInfo of(int page, int size, long totalElements) {
            int totalPages = (int) Math.ceil((double) totalElements / size);
            long firstElement = (long) page * size;
            long lastElement = Math.min(firstElement + size - 1, totalElements - 1);
            
            return PaginationInfo.builder()
                    .currentPage(page)
                    .pageSize(size)
                    .totalElements(totalElements)
                    .totalPages(totalPages)
                    .hasNext(page < totalPages - 1)
                    .hasPrevious(page > 0)
                    .firstElement(firstElement)
                    .lastElement(Math.max(0, lastElement))
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Filter result summary")
    public static class FilterSummary {

        @Schema(description = "Total matched entries")
        private long totalMatched;

        @Schema(description = "Count per log level")
        private Map<String, Long> levelCounts;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
        @Schema(description = "Earliest log timestamp")
        private LocalDateTime earliestLog;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
        @Schema(description = "Latest log timestamp")
        private LocalDateTime latestLog;

        @Schema(description = "Error count")
        private long errorCount;

        @Schema(description = "Stack trace count")
        private long stackTraceCount;

        @Schema(description = "Top loggers in results")
        private List<FieldCount> topLoggers;

        @Schema(description = "Top threads in results")
        private List<FieldCount> topThreads;

        @Schema(description = "Top sources in results")
        private List<FieldCount> topSources;

        @Schema(description = "Top hostnames in results")
        private List<FieldCount> topHostnames;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Field value with count")
    public static class FieldCount {
        
        @Schema(description = "Field value")
        private String value;
        
        @Schema(description = "Count")
        private long count;
    }

    public static LogQueryResponse empty(int page, int size) {
        return LogQueryResponse.builder()
                .logs(List.of())
                .pagination(PaginationInfo.of(page, size, 0))
                .build();
    }

    public static LogQueryResponse of(List<LogEntry> logs, int page, int size, long totalElements) {
        return LogQueryResponse.builder()
                .logs(logs)
                .pagination(PaginationInfo.of(page, size, totalElements))
                .build();
    }
}
