package com.star.logscanner.dto.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO for log search queries.
 * 
 * <p>Supports complex filtering, full-text search, pagination, and sorting.
 * All filters use AND logic (combined filters narrow results).
 * 
 * <p>Example usage:
 * <pre>{@code
 * {
 *   "jobId": "abc-123-def",
 *   "searchText": "exception",
 *   "levels": ["ERROR", "WARN"],
 *   "startDate": "2024-01-15T00:00:00",
 *   "endDate": "2024-01-15T23:59:59",
 *   "page": 0,
 *   "size": 50,
 *   "sortBy": "timestamp",
 *   "sortDirection": "desc"
 * }
 * }</pre>
 * 
 * @author Eshmamatov Obidjon
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Log search query request")
public class LogQueryRequest {

    @NotBlank(message = "Job ID is required")
    @Schema(description = "Job ID to query logs for", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
    private String jobId;

    @Schema(description = "Full-text search query", example = "NullPointerException")
    private String searchText;

    @Schema(description = "Fields to search in", example = "[\"message\", \"rawLine\"]")
    private List<String> searchFields;

    @Schema(description = "Log levels to include", example = "[\"ERROR\", \"WARN\"]")
    private List<String> levels;

    @Schema(description = "Filter by file name", example = "application.log")
    private String fileName;

    @Schema(description = "Filter by logger name", example = "com.example.MyService")
    private String logger;

    @Schema(description = "Filter by thread name", example = "main")
    private String thread;

    @Schema(description = "Filter by source", example = "UserService")
    private String source;

    @Schema(description = "Filter by hostname", example = "server-001")
    private String hostname;

    @Schema(description = "Filter by application", example = "my-service")
    private String application;

    @Schema(description = "Filter by environment", example = "production")
    private String environment;

    @Schema(description = "Filter by error flag")
    private Boolean hasError;

    @Schema(description = "Filter by stack trace presence")
    private Boolean hasStackTrace;

    @Schema(description = "Filter by tags", example = "[\"important\", \"reviewed\"]")
    private List<String> tags;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Start date/time for filtering", example = "2024-01-15T00:00:00")
    private LocalDateTime startDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "End date/time for filtering", example = "2024-01-15T23:59:59")
    private LocalDateTime endDate;

    @Schema(description = "Minimum line number")
    private Long minLineNumber;

    @Schema(description = "Maximum line number")
    private Long maxLineNumber;

    @Schema(description = "Field to sort by", example = "timestamp", defaultValue = "timestamp")
    @Builder.Default
    private String sortBy = "timestamp";

    @Schema(description = "Sort direction", example = "desc", defaultValue = "desc", allowableValues = {"asc", "desc"})
    @Builder.Default
    private String sortDirection = "desc";

    @Min(value = 0, message = "Page number must be >= 0")
    @Schema(description = "Page number (0-based)", example = "0", defaultValue = "0")
    @Builder.Default
    private Integer page = 0;

    @Min(value = 1, message = "Page size must be >= 1")
    @Max(value = 1000, message = "Page size must be <= 1000")
    @Schema(description = "Page size", example = "50", defaultValue = "50")
    @Builder.Default
    private Integer size = 50;

    @Schema(description = "Fields to include in response")
    private List<String> includeFields;

    @Schema(description = "Fields to exclude from response")
    private List<String> excludeFields;

    @Schema(description = "Include aggregation summary", defaultValue = "true")
    @Builder.Default
    private Boolean includeSummary = true;

    @Schema(description = "Highlight search terms")
    @Builder.Default
    private Boolean highlightMatches = false;

    public List<String> getEffectiveSearchFields() {
        if (searchFields == null || searchFields.isEmpty()) {
            return List.of("message", "rawLine", "stackTrace");
        }
        return searchFields;
    }

    public boolean hasFilters() {
        return searchText != null ||
                (levels != null && !levels.isEmpty()) ||
                fileName != null ||
                logger != null ||
                thread != null ||
                source != null ||
                hostname != null ||
                application != null ||
                environment != null ||
                hasError != null ||
                hasStackTrace != null ||
                (tags != null && !tags.isEmpty()) ||
                startDate != null ||
                endDate != null ||
                minLineNumber != null ||
                maxLineNumber != null;
    }

    public String getEffectiveSortDirection() {
        if (sortDirection == null || 
            (!sortDirection.equalsIgnoreCase("asc") && !sortDirection.equalsIgnoreCase("desc"))) {
            return "desc";
        }
        return sortDirection.toLowerCase();
    }
}
