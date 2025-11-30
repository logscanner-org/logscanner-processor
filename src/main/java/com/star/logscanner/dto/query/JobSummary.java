package com.star.logscanner.dto.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Job summary statistics")
public class JobSummary {
    
    @Schema(description = "Job ID")
    private String jobId;
    
    @Schema(description = "Original file name")
    private String fileName;
    
    @Schema(description = "File size in bytes")
    private Long fileSize;
    
    @Schema(description = "Job status")
    private String status;
    
    @Schema(description = "Total lines in file")
    private Long totalLines;
    
    @Schema(description = "Successfully parsed lines")
    private Long successfulLines;
    
    @Schema(description = "Failed to parse lines")
    private Long failedLines;
    
    @Schema(description = "Total indexed entries")
    private Long totalEntries;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Schema(description = "Earliest log timestamp")
    private LocalDateTime earliestTimestamp;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Schema(description = "Latest log timestamp")
    private LocalDateTime latestTimestamp;
    
    @Schema(description = "Time span in seconds")
    private Long timeSpanSeconds;
    
    @Schema(description = "Count per log level")
    private Map<String, Long> levelCounts;
    
    @Schema(description = "Total error entries")
    private Long errorCount;
    
    @Schema(description = "Total warning entries")
    private Long warningCount;
    
    @Schema(description = "Entries with stack traces")
    private Long stackTraceCount;
    
    @Schema(description = "Unique logger count")
    private Integer uniqueLoggerCount;
    
    @Schema(description = "Unique thread count")
    private Integer uniqueThreadCount;
    
    @Schema(description = "Unique source count")
    private Integer uniqueSourceCount;
    
    @Schema(description = "Unique hostname count")
    private Integer uniqueHostnameCount;
    
    @Schema(description = "Top loggers by count")
    private List<LogQueryResponse.FieldCount> topLoggers;
    
    @Schema(description = "Top threads by count")
    private List<LogQueryResponse.FieldCount> topThreads;
    
    @Schema(description = "Top sources by count")
    private List<LogQueryResponse.FieldCount> topSources;
    
    @Schema(description = "Most common error messages")
    private List<LogQueryResponse.FieldCount> topErrorMessages;
    
    @Schema(description = "Processing time in milliseconds")
    private Long processingTimeMs;
    
    @Schema(description = "Lines processed per second")
    private Double linesPerSecond;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Schema(description = "Processing started at")
    private LocalDateTime startedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Schema(description = "Processing completed at")
    private LocalDateTime completedAt;
}
