package com.star.logscanner.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import com.star.logscanner.dto.query.*;
import com.star.logscanner.entity.JobStatus;
import com.star.logscanner.entity.LogEntry;
import com.star.logscanner.exception.JobNotFoundException;
import com.star.logscanner.query.LogQueryBuilder;
import com.star.logscanner.repository.JobStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for querying and searching log entries.
 * 
 * <p>Provides:
 * <ul>
 *   <li>Full-text search with filtering</li>
 *   <li>Job summary statistics</li>
 *   <li>Timeline data for charts</li>
 *   <li>Field value extraction for filters</li>
 *   <li>Log export functionality</li>
 * </ul>
 * 
 * @author LogScanner Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogQueryService {
    
    private final ElasticsearchOperations elasticsearchOperations;
    private final JobStatusRepository jobStatusRepository;
    private final LogQueryBuilder queryBuilder;
    private final ObjectMapper objectMapper;

    public LogQueryResponse searchLogs(LogQueryRequest request) {
        long startTime = System.currentTimeMillis();
        
        log.info("Searching logs for job: {}, page: {}, size: {}", 
                request.getJobId(), request.getPage(), request.getSize());
        
        // Verify job exists
        verifyJobExists(request.getJobId());
        
        // Build and execute query
        NativeQuery query = queryBuilder.buildSearchQuery(request);
        SearchHits<LogEntry> searchHits = elasticsearchOperations.search(query, LogEntry.class);
        
        // Extract results
        List<LogEntry> logs = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
        
        long totalElements = searchHits.getTotalHits();
        
        // Build response
        LogQueryResponse.LogQueryResponseBuilder responseBuilder = LogQueryResponse.builder()
                .logs(logs)
                .pagination(LogQueryResponse.PaginationInfo.of(
                        request.getPage() != null ? request.getPage() : 0,
                        request.getSize() != null ? request.getSize() : 50,
                        totalElements
                ))
                .queryTimeMs(System.currentTimeMillis() - startTime);
        
        // Add summary if requested
        if (Boolean.TRUE.equals(request.getIncludeSummary())) {
            responseBuilder.summary(extractSummary(searchHits, totalElements));
        }
        
        // Add highlights if requested
        if (Boolean.TRUE.equals(request.getHighlightMatches())) {
            responseBuilder.highlights(extractHighlights(searchHits));
        }
        
        log.info("Search completed: {} results in {} ms", 
                logs.size(), System.currentTimeMillis() - startTime);
        
        return responseBuilder.build();
    }

    public JobSummary getJobSummary(String jobId) {
        log.info("Getting summary for job: {}", jobId);
        
        // Get job status info
        JobStatus jobStatus = jobStatusRepository.findByJobId(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        
        // Execute aggregation query
        NativeQuery query = queryBuilder.buildAggregationQuery(jobId);
        SearchHits<LogEntry> searchHits = elasticsearchOperations.search(query, LogEntry.class);
        
        Map<String, Aggregate> aggMap = extractAggregationsMap(searchHits);
        
        JobSummary.JobSummaryBuilder builder = JobSummary.builder()
                .jobId(jobId)
                .fileName(jobStatus.getFileName())
                .fileSize(jobStatus.getFileSize())
                .status(jobStatus.getStatus() != null ? jobStatus.getStatus().name() : null)
                .totalLines(jobStatus.getTotalLines())
                .successfulLines(jobStatus.getSuccessfulLines())
                .failedLines(jobStatus.getFailedLines())
                .totalEntries(searchHits.getTotalHits())
                .processingTimeMs(jobStatus.getProcessingTimeMs())
                .linesPerSecond(jobStatus.getLinesPerSecond())
                .startedAt(jobStatus.getStartedAt())
                .completedAt(jobStatus.getCompletedAt());
        
        // Level counts
        if (aggMap.containsKey("level_counts")) {
            builder.levelCounts(extractTermsAggregation(aggMap.get("level_counts")));
        }
        
        // Error/warning counts
        if (aggMap.containsKey("error_count")) {
            builder.errorCount(extractFilterCount(aggMap.get("error_count")));
        }
        
        if (aggMap.containsKey("stacktrace_count")) {
            builder.stackTraceCount(extractFilterCount(aggMap.get("stacktrace_count")));
        }
        
        // Warning count from level counts
        Map<String, Long> levelCounts = builder.build().getLevelCounts();
        if (levelCounts != null) {
            builder.warningCount(levelCounts.getOrDefault("WARN", 0L));
        }
        
        // Timestamps
        if (aggMap.containsKey("min_timestamp")) {
            builder.earliestTimestamp(extractDateAggregation(aggMap.get("min_timestamp")));
        }
        if (aggMap.containsKey("max_timestamp")) {
            builder.latestTimestamp(extractDateAggregation(aggMap.get("max_timestamp")));
        }
        
        // Calculate time span
        LocalDateTime earliest = builder.build().getEarliestTimestamp();
        LocalDateTime latest = builder.build().getLatestTimestamp();
        if (earliest != null && latest != null) {
            builder.timeSpanSeconds(java.time.Duration.between(earliest, latest).getSeconds());
        }
        
        // Top values
        if (aggMap.containsKey("top_loggers")) {
            builder.topLoggers(extractFieldCounts(aggMap.get("top_loggers")));
        }
        if (aggMap.containsKey("top_threads")) {
            builder.topThreads(extractFieldCounts(aggMap.get("top_threads")));
        }
        if (aggMap.containsKey("top_sources")) {
            builder.topSources(extractFieldCounts(aggMap.get("top_sources")));
        }
        
        // Cardinality
        if (aggMap.containsKey("unique_loggers")) {
            builder.uniqueLoggerCount(extractCardinality(aggMap.get("unique_loggers")));
        }
        if (aggMap.containsKey("unique_threads")) {
            builder.uniqueThreadCount(extractCardinality(aggMap.get("unique_threads")));
        }
        
        return builder.build();
    }
    
    public Map<String, Long> getLevelDistribution(String jobId) {
        log.debug("Getting level distribution for job: {}", jobId);
        
        verifyJobExists(jobId);
        
        NativeQuery query = queryBuilder.buildAggregationQuery(jobId);
        SearchHits<LogEntry> searchHits = elasticsearchOperations.search(query, LogEntry.class);
        
        Map<String, Aggregate> aggMap = extractAggregationsMap(searchHits);
        
        if (aggMap.containsKey("level_counts")) {
            return extractTermsAggregation(aggMap.get("level_counts"));
        }
        
        return Map.of();
    }
    

    public TimelineData getTimelineData(String jobId, String intervalStr) {
        log.info("Getting timeline data for job: {}, interval: {}", jobId, intervalStr);
        
        verifyJobExists(jobId);
        
        TimelineData.Interval interval = TimelineData.Interval.parse(intervalStr);
        NativeQuery query = queryBuilder.buildTimelineQuery(jobId, interval);
        SearchHits<LogEntry> searchHits = elasticsearchOperations.search(query, LogEntry.class);
        
        Map<String, Aggregate> aggMap = extractAggregationsMap(searchHits);
        
        List<TimelineData.TimelineBucket> buckets = new ArrayList<>();
        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        
        Aggregate timelineAgg = aggMap.get("timeline");
        
        if (timelineAgg != null && timelineAgg.isDateHistogram()) {
            List<DateHistogramBucket> histBuckets = timelineAgg.dateHistogram().buckets().array();
            
            for (DateHistogramBucket bucket : histBuckets) {
                LocalDateTime timestamp = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(bucket.key()),
                        ZoneId.systemDefault()
                );
                
                long errorCount = 0;
                long warnCount = 0;
                
                if (bucket.aggregations() != null) {
                    Aggregate errorAgg = bucket.aggregations().get("error_count");
                    if (errorAgg != null && errorAgg.isFilter()) {
                        errorCount = errorAgg.filter().docCount();
                    }
                    
                    Aggregate warnAgg = bucket.aggregations().get("warn_count");
                    if (warnAgg != null && warnAgg.isFilter()) {
                        warnCount = warnAgg.filter().docCount();
                    }
                }
                
                buckets.add(TimelineData.TimelineBucket.builder()
                        .timestamp(timestamp)
                        .count(bucket.docCount())
                        .errorCount(errorCount)
                        .warningCount(warnCount)
                        .build());
                
                if (startTime == null || timestamp.isBefore(startTime)) {
                    startTime = timestamp;
                }
                if (endTime == null || timestamp.isAfter(endTime)) {
                    endTime = timestamp;
                }
            }
        }
        
        return TimelineData.builder()
                .jobId(jobId)
                .interval(interval.getValue())
                .startTime(startTime)
                .endTime(endTime)
                .totalCount(searchHits.getTotalHits())
                .buckets(buckets)
                .build();
    }

    public List<LogQueryResponse.FieldCount> getUniqueFieldValues(String jobId, String fieldName, int limit) {
        log.debug("Getting unique values for field '{}' in job: {}", fieldName, jobId);
        
        verifyJobExists(jobId);
        
        NativeQuery query = queryBuilder.buildUniqueValuesQuery(jobId, fieldName, limit);
        SearchHits<LogEntry> searchHits = elasticsearchOperations.search(query, LogEntry.class);
        
        Map<String, Aggregate> aggMap = extractAggregationsMap(searchHits);
        
        if (aggMap.containsKey("unique_values")) {
            return extractFieldCounts(aggMap.get("unique_values"));
        }
        
        return List.of();
    }

    public List<String> getUniqueFieldValuesList(String jobId, String fieldName) {
        return getUniqueFieldValues(jobId, fieldName, 100).stream()
                .map(LogQueryResponse.FieldCount::getValue)
                .collect(Collectors.toList());
    }

    public byte[] exportToCsv(String jobId, ExportRequest request) {
        log.info("Exporting logs to CSV for job: {}", jobId);
        
        // Build query from export request
        LogQueryRequest queryRequest = request.getQuery() != null 
                ? request.getQuery() 
                : LogQueryRequest.builder().jobId(jobId).build();
        
        queryRequest.setJobId(jobId);
        queryRequest.setSize(request.getMaxRecords());
        queryRequest.setPage(0);
        queryRequest.setIncludeSummary(false);
        
        // Fetch logs
        LogQueryResponse response = searchLogs(queryRequest);
        List<LogEntry> logs = response.getLogs();
        List<String> fields = request.getEffectiveFields();
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             CSVWriter writer = new CSVWriter(
                     new OutputStreamWriter(baos, StandardCharsets.UTF_8),
                     request.getDelimiter().charAt(0),
                     CSVWriter.DEFAULT_QUOTE_CHARACTER,
                     CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                     CSVWriter.DEFAULT_LINE_END
             )) {
            
            // Write headers
            if (Boolean.TRUE.equals(request.getIncludeHeaders())) {
                writer.writeNext(fields.toArray(new String[0]));
            }
            
            // Write data rows
            for (LogEntry logEntry : logs) {
                String[] row = fields.stream()
                        .map(field -> extractFieldValue(logEntry, field))
                        .toArray(String[]::new);
                writer.writeNext(row);
            }
            
            writer.flush();
            return baos.toByteArray();
            
        } catch (Exception e) {
            log.error("Failed to export logs to CSV", e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    public byte[] exportToJson(String jobId, ExportRequest request) {
        log.info("Exporting logs to JSON for job: {}", jobId);
        
        LogQueryRequest queryRequest = request.getQuery() != null 
                ? request.getQuery() 
                : LogQueryRequest.builder().jobId(jobId).build();
        
        queryRequest.setJobId(jobId);
        queryRequest.setSize(request.getMaxRecords());
        queryRequest.setPage(0);
        queryRequest.setIncludeSummary(false);
        
        LogQueryResponse response = searchLogs(queryRequest);
        
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(response.getLogs());
        } catch (Exception e) {
            log.error("Failed to export logs to JSON", e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    public byte[] exportToNdjson(String jobId, ExportRequest request) {
        log.info("Exporting logs to NDJSON for job: {}", jobId);
        
        LogQueryRequest queryRequest = request.getQuery() != null 
                ? request.getQuery() 
                : LogQueryRequest.builder().jobId(jobId).build();
        
        queryRequest.setJobId(jobId);
        queryRequest.setSize(request.getMaxRecords());
        queryRequest.setPage(0);
        queryRequest.setIncludeSummary(false);
        
        LogQueryResponse response = searchLogs(queryRequest);
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (LogEntry logEntry : response.getLogs()) {
                baos.write(objectMapper.writeValueAsBytes(logEntry));
                baos.write('\n');
            }
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to export logs to NDJSON", e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }
    
    private void verifyJobExists(String jobId) {
        if (!jobStatusRepository.existsById(jobId)) {
            throw new JobNotFoundException(jobId);
        }
    }

    private Map<String, Aggregate> extractAggregationsMap(SearchHits<LogEntry> searchHits) {
        Map<String, Aggregate> aggMap = new HashMap<>();
        
        if (searchHits.getAggregations() == null) {
            return aggMap;
        }
        
        ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
        
        for (ElasticsearchAggregation esAgg : aggregations.aggregations()) {
            aggMap.put(esAgg.aggregation().getName(), esAgg.aggregation().getAggregate());
        }
        
        return aggMap;
    }
    
    private LogQueryResponse.FilterSummary extractSummary(SearchHits<LogEntry> searchHits, long totalMatched) {
        LogQueryResponse.FilterSummary.FilterSummaryBuilder builder = LogQueryResponse.FilterSummary.builder()
                .totalMatched(totalMatched);
        
        Map<String, Aggregate> aggMap = extractAggregationsMap(searchHits);
        
        if (aggMap.containsKey("level_counts")) {
            builder.levelCounts(extractTermsAggregation(aggMap.get("level_counts")));
        }
        if (aggMap.containsKey("error_count")) {
            builder.errorCount(extractFilterCount(aggMap.get("error_count")));
        }
        if (aggMap.containsKey("stacktrace_count")) {
            builder.stackTraceCount(extractFilterCount(aggMap.get("stacktrace_count")));
        }
        if (aggMap.containsKey("min_timestamp")) {
            builder.earliestLog(extractDateAggregation(aggMap.get("min_timestamp")));
        }
        if (aggMap.containsKey("max_timestamp")) {
            builder.latestLog(extractDateAggregation(aggMap.get("max_timestamp")));
        }
        
        return builder.build();
    }
    
    private Map<String, Map<String, List<String>>> extractHighlights(SearchHits<LogEntry> searchHits) {
        Map<String, Map<String, List<String>>> highlights = new HashMap<>();
        
        for (SearchHit<LogEntry> hit : searchHits.getSearchHits()) {
            if (!hit.getHighlightFields().isEmpty()) {
                highlights.put(hit.getId(), hit.getHighlightFields());
            }
        }
        
        return highlights.isEmpty() ? null : highlights;
    }
    
    private Map<String, Long> extractTermsAggregation(Aggregate aggregate) {
        if (aggregate == null) return Map.of();
        
        Map<String, Long> result = new LinkedHashMap<>();
        
        if (aggregate.isSterms()) {
            for (StringTermsBucket bucket : aggregate.sterms().buckets().array()) {
                result.put(bucket.key().stringValue(), bucket.docCount());
            }
        }
        
        return result;
    }
    
    private long extractFilterCount(Aggregate aggregate) {
        if (aggregate == null) return 0;
        if (aggregate.isFilter()) {
            return aggregate.filter().docCount();
        }
        return 0;
    }
    
    private LocalDateTime extractDateAggregation(Aggregate aggregate) {
        if (aggregate == null) return null;
        
        Double value = null;
        if (aggregate.isMin()) {
            value = aggregate.min().value();
        } else if (aggregate.isMax()) {
            value = aggregate.max().value();
        }
        
        if (value != null && !value.isNaN() && !value.isInfinite()) {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(value.longValue()),
                    ZoneId.systemDefault()
            );
        }
        
        return null;
    }
    
    private List<LogQueryResponse.FieldCount> extractFieldCounts(Aggregate aggregate) {
        if (aggregate == null) return List.of();
        
        List<LogQueryResponse.FieldCount> result = new ArrayList<>();
        
        if (aggregate.isSterms()) {
            for (StringTermsBucket bucket : aggregate.sterms().buckets().array()) {
                result.add(LogQueryResponse.FieldCount.builder()
                        .value(bucket.key().stringValue())
                        .count(bucket.docCount())
                        .build());
            }
        }
        
        return result;
    }
    
    private Integer extractCardinality(Aggregate aggregate) {
        if (aggregate == null) return null;
        if (aggregate.isCardinality()) {
            return (int) aggregate.cardinality().value();
        }
        return null;
    }
    
    private String extractFieldValue(LogEntry logEntry, String fieldName) {
        if (logEntry == null) return "";
        
        return switch (fieldName) {
            case "id" -> logEntry.getId() != null ? logEntry.getId() : "";
            case "jobId" -> logEntry.getJobId() != null ? logEntry.getJobId() : "";
            case "timestamp" -> logEntry.getTimestamp() != null ? logEntry.getTimestamp().toString() : "";
            case "level" -> logEntry.getLevel() != null ? logEntry.getLevel() : "";
            case "message" -> logEntry.getMessage() != null ? logEntry.getMessage() : "";
            case "logger" -> logEntry.getLogger() != null ? logEntry.getLogger() : "";
            case "thread" -> logEntry.getThread() != null ? logEntry.getThread() : "";
            case "source" -> logEntry.getSource() != null ? logEntry.getSource() : "";
            case "lineNumber" -> logEntry.getLineNumber() != null ? logEntry.getLineNumber().toString() : "";
            case "rawLine" -> logEntry.getRawLine() != null ? logEntry.getRawLine() : "";
            case "fileName" -> logEntry.getFileName() != null ? logEntry.getFileName() : "";
            case "stackTrace" -> logEntry.getStackTrace() != null ? logEntry.getStackTrace() : "";
            case "hostname" -> logEntry.getHostname() != null ? logEntry.getHostname() : "";
            case "application" -> logEntry.getApplication() != null ? logEntry.getApplication() : "";
            case "environment" -> logEntry.getEnvironment() != null ? logEntry.getEnvironment() : "";
            case "hasError" -> logEntry.getHasError() != null ? logEntry.getHasError().toString() : "";
            case "hasStackTrace" -> logEntry.getHasStackTrace() != null ? logEntry.getHasStackTrace().toString() : "";
            default -> "";
        };
    }
}
