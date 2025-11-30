package com.star.logscanner.query;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.json.JsonData;
import com.star.logscanner.dto.query.LogQueryRequest;
import com.star.logscanner.dto.query.TimelineData;
import com.star.logscanner.exception.InvalidQueryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class LogQueryBuilder {
    
    private static final DateTimeFormatter ES_DATE_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "timestamp", "lineNumber", "level", "logger", "thread", 
            "source", "hostname", "application", "indexedAt"
    );

    private static final Set<String> KEYWORD_FIELDS = Set.of(
            "jobId", "level", "logger", "thread", "source", "fileName",
            "hostname", "application", "environment"
    );

    private static final Set<String> TEXT_FIELDS = Set.of(
            "message", "rawLine", "stackTrace"
    );
    

    public NativeQuery buildSearchQuery(LogQueryRequest request) {
        validateRequest(request);
        
        log.debug("Building search query for job: {}, filters: {}", 
                request.getJobId(), request.hasFilters());
        
        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(buildBoolQuery(request))
                .withPageable(buildPageable(request))
                .withSort(buildSort(request));
        
        // Add highlighting if requested
        if (Boolean.TRUE.equals(request.getHighlightMatches()) && request.getSearchText() != null) {
            queryBuilder.withHighlightQuery(buildHighlightQuery(request));
        }
        
        // Add aggregations if summary is requested
        if (Boolean.TRUE.equals(request.getIncludeSummary())) {
            addAggregations(queryBuilder);
        }
        
        // Add source filtering if specified
        if (request.getIncludeFields() != null || request.getExcludeFields() != null) {
            queryBuilder.withSourceFilter(new org.springframework.data.elasticsearch.core.query.SourceFilter() {
                @Override
                public String[] getIncludes() {
                    return request.getIncludeFields() != null 
                            ? request.getIncludeFields().toArray(new String[0]) 
                            : null;
                }
                
                @Override
                public String[] getExcludes() {
                    return request.getExcludeFields() != null 
                            ? request.getExcludeFields().toArray(new String[0]) 
                            : null;
                }
            });
        }
        
        return queryBuilder.build();
    }

    public NativeQuery buildCountQuery(LogQueryRequest request) {
        return NativeQuery.builder()
                .withQuery(buildBoolQuery(request))
                .withPageable(PageRequest.of(0, 1)) // Only need count
                .withTrackTotalHits(true)
                .build();
    }

    public NativeQuery buildAggregationQuery(String jobId) {
        Query jobFilter = QueryBuilders.term(t -> t.field("jobId").value(jobId));
        
        NativeQueryBuilder builder = NativeQuery.builder()
                .withQuery(jobFilter)
                .withPageable(PageRequest.of(0, 1)) // Minimum size, we only need aggregations
                .withTrackTotalHits(true);
        
        addAggregations(builder);
        addExtendedAggregations(builder);
        
        return builder.build();
    }

    public NativeQuery buildTimelineQuery(String jobId, TimelineData.Interval interval) {
        Query jobFilter = QueryBuilders.term(t -> t.field("jobId").value(jobId));
        
        return NativeQuery.builder()
                .withQuery(jobFilter)
                .withPageable(PageRequest.of(0, 1)) // Minimum size, we only need aggregations
                .withAggregation("timeline", Aggregation.of(a -> a
                        .dateHistogram(dh -> {
                            dh.field("timestamp");
                            dh.minDocCount(0);
                            
                            // Use calendar interval for standard units, fixed interval for multiples
                            switch (interval) {
                                case SECOND -> dh.fixedInterval(fi -> fi.time("1s"));
                                case MINUTE -> dh.calendarInterval(co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval.Minute);
                                case FIVE_MINUTES -> dh.fixedInterval(fi -> fi.time("5m"));
                                case FIFTEEN_MINUTES -> dh.fixedInterval(fi -> fi.time("15m"));
                                case THIRTY_MINUTES -> dh.fixedInterval(fi -> fi.time("30m"));
                                case HOUR -> dh.calendarInterval(co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval.Hour);
                                case DAY -> dh.calendarInterval(co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval.Day);
                                case WEEK -> dh.calendarInterval(co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval.Week);
                                case MONTH -> dh.calendarInterval(co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval.Month);
                            }
                            
                            return dh;
                        })
                        .aggregations(Map.of(
                                "error_count", Aggregation.of(sub -> sub
                                        .filter(f -> f.term(t -> t.field("level").value("ERROR")))
                                ),
                                "warn_count", Aggregation.of(sub -> sub
                                        .filter(f -> f.term(t -> t.field("level").value("WARN")))
                                )
                        ))
                ))
                .build();
    }

    public NativeQuery buildUniqueValuesQuery(String jobId, String fieldName, int limit) {
        if (!KEYWORD_FIELDS.contains(fieldName)) {
            throw InvalidQueryException.unsupportedField(fieldName);
        }
        
        Query jobFilter = QueryBuilders.term(t -> t.field("jobId").value(jobId));
        
        return NativeQuery.builder()
                .withQuery(jobFilter)
                .withPageable(PageRequest.of(0, 1)) // Minimum size, we only need aggregations
                .withAggregation("unique_values", Aggregation.of(a -> a
                        .terms(t -> t
                                .field(fieldName)
                                .size(limit)
                        )
                ))
                .build();
    }

    private Query buildBoolQuery(LogQueryRequest request) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        
        // Must: Job ID (required)
        boolBuilder.must(QueryBuilders.term(t -> t.field("jobId").value(request.getJobId())));
        
        // Should: Full-text search (if provided)
        if (request.getSearchText() != null && !request.getSearchText().isBlank()) {
            boolBuilder.must(buildFullTextQuery(request.getSearchText(), request.getEffectiveSearchFields()));
        }
        
        // Filter: All other filters (using filter context for better performance)
        List<Query> filters = buildFilters(request);
        for (Query filter : filters) {
            boolBuilder.filter(filter);
        }
        
        return boolBuilder.build()._toQuery();
    }
    
    private Query buildFullTextQuery(String searchText, List<String> fields) {
        // Validate fields
        for (String field : fields) {
            if (!TEXT_FIELDS.contains(field) && !KEYWORD_FIELDS.contains(field)) {
                log.warn("Unknown search field '{}', might not return expected results", field);
            }
        }
        
        return QueryBuilders.multiMatch(mm -> mm
                .query(searchText)
                .fields(fields)
                .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                .fuzziness("AUTO")
                .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
        );
    }
    
    private List<Query> buildFilters(LogQueryRequest request) {
        List<Query> filters = new ArrayList<>();
        
        // Level filter (OR within levels)
        if (request.getLevels() != null && !request.getLevels().isEmpty()) {
            filters.add(QueryBuilders.terms(t -> t
                    .field("level")
                    .terms(tv -> tv.value(request.getLevels().stream()
                            .map(l -> co.elastic.clients.elasticsearch._types.FieldValue.of(l.toUpperCase()))
                            .toList()))
            ));
        }
        
        // Keyword field filters
        addKeywordFilter(filters, "fileName", request.getFileName());
        addKeywordFilter(filters, "logger", request.getLogger());
        addKeywordFilter(filters, "thread", request.getThread());
        addKeywordFilter(filters, "source", request.getSource());
        addKeywordFilter(filters, "hostname", request.getHostname());
        addKeywordFilter(filters, "application", request.getApplication());
        addKeywordFilter(filters, "environment", request.getEnvironment());
        
        // Boolean filters
        if (request.getHasError() != null) {
            filters.add(QueryBuilders.term(t -> t.field("hasError").value(request.getHasError())));
        }
        
        if (request.getHasStackTrace() != null) {
            filters.add(QueryBuilders.term(t -> t.field("hasStackTrace").value(request.getHasStackTrace())));
        }
        
        // Tags filter (OR within tags)
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            filters.add(QueryBuilders.terms(t -> t
                    .field("tags")
                    .terms(tv -> tv.value(request.getTags().stream()
                            .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                            .toList()))
            ));
        }
        
        // Date range filter
        if (request.getStartDate() != null || request.getEndDate() != null) {
            filters.add(buildDateRangeQuery(request));
        }
        
        // Line number range filter
        if (request.getMinLineNumber() != null || request.getMaxLineNumber() != null) {
            filters.add(buildLineNumberRangeQuery(request));
        }
        
        return filters;
    }
    
    private void addKeywordFilter(List<Query> filters, String field, String value) {
        if (value != null && !value.isBlank()) {
            // Support wildcard matching
            if (value.contains("*") || value.contains("?")) {
                filters.add(QueryBuilders.wildcard(w -> w.field(field).value(value)));
            } else {
                filters.add(QueryBuilders.term(t -> t.field(field).value(value)));
            }
        }
    }
    
    private Query buildDateRangeQuery(LogQueryRequest request) {
        return QueryBuilders.range(r -> {
            r.field("timestamp");
            
            if (request.getStartDate() != null) {
                r.gte(JsonData.of(request.getStartDate().format(ES_DATE_FORMAT)));
            }
            if (request.getEndDate() != null) {
                r.lte(JsonData.of(request.getEndDate().format(ES_DATE_FORMAT)));
            }
            
            return r;
        });
    }
    
    private Query buildLineNumberRangeQuery(LogQueryRequest request) {
        return QueryBuilders.range(r -> {
            r.field("lineNumber");
            
            if (request.getMinLineNumber() != null) {
                r.gte(JsonData.of(request.getMinLineNumber()));
            }
            if (request.getMaxLineNumber() != null) {
                r.lte(JsonData.of(request.getMaxLineNumber()));
            }
            
            return r;
        });
    }
    
    private Sort buildSort(LogQueryRequest request) {
        String sortField = request.getSortBy();
        
        // Validate sort field
        if (!SORTABLE_FIELDS.contains(sortField)) {
            throw InvalidQueryException.invalidSortField(sortField);
        }
        
        Sort.Direction direction = "asc".equalsIgnoreCase(request.getEffectiveSortDirection()) 
                ? Sort.Direction.ASC 
                : Sort.Direction.DESC;
        
        return Sort.by(direction, sortField);
    }
    
    private Pageable buildPageable(LogQueryRequest request) {
        return PageRequest.of(
                request.getPage() != null ? request.getPage() : 0,
                request.getSize() != null ? request.getSize() : 50
        );
    }
    
    private HighlightQuery buildHighlightQuery(LogQueryRequest request) {
        List<HighlightField> highlightFields = request.getEffectiveSearchFields().stream()
                .map(HighlightField::new)
                .toList();
        
        return new HighlightQuery(
                new Highlight(
                        HighlightParameters.builder()
                                .withPreTags("<em class=\"highlight\">")
                                .withPostTags("</em>")
                                .withFragmentSize(150)
                                .withNumberOfFragments(3)
                                .build(),
                        highlightFields
                ),
                null // Use default highlighter
        );
    }
    
    private void addAggregations(NativeQueryBuilder builder) {
        // Level distribution
        builder.withAggregation("level_counts", Aggregation.of(a -> a
                .terms(t -> t.field("level").size(10))
        ));
        
        // Error count
        builder.withAggregation("error_count", Aggregation.of(a -> a
                .filter(f -> f.term(t -> t.field("hasError").value(true)))
        ));
        
        // Stack trace count
        builder.withAggregation("stacktrace_count", Aggregation.of(a -> a
                .filter(f -> f.term(t -> t.field("hasStackTrace").value(true)))
        ));
        
        // Min/max timestamps
        builder.withAggregation("min_timestamp", Aggregation.of(a -> a
                .min(m -> m.field("timestamp"))
        ));
        
        builder.withAggregation("max_timestamp", Aggregation.of(a -> a
                .max(m -> m.field("timestamp"))
        ));
    }
    
    private void addExtendedAggregations(NativeQueryBuilder builder) {
        // Top loggers
        builder.withAggregation("top_loggers", Aggregation.of(a -> a
                .terms(t -> t.field("logger").size(10))
        ));
        
        // Top threads
        builder.withAggregation("top_threads", Aggregation.of(a -> a
                .terms(t -> t.field("thread").size(10))
        ));
        
        // Top sources
        builder.withAggregation("top_sources", Aggregation.of(a -> a
                .terms(t -> t.field("source").size(10))
        ));
        
        // Top hostnames
        builder.withAggregation("top_hostnames", Aggregation.of(a -> a
                .terms(t -> t.field("hostname").size(10))
        ));
        
        // Cardinality counts
        builder.withAggregation("unique_loggers", Aggregation.of(a -> a
                .cardinality(c -> c.field("logger"))
        ));
        
        builder.withAggregation("unique_threads", Aggregation.of(a -> a
                .cardinality(c -> c.field("thread"))
        ));
    }
    
    private void validateRequest(LogQueryRequest request) {
        if (request.getJobId() == null || request.getJobId().isBlank()) {
            throw new InvalidQueryException("Job ID is required");
        }
        
        // Validate date range
        if (request.getStartDate() != null && request.getEndDate() != null) {
            if (request.getStartDate().isAfter(request.getEndDate())) {
                throw InvalidQueryException.invalidDateRange();
            }
        }
        
        // Validate line number range
        if (request.getMinLineNumber() != null && request.getMaxLineNumber() != null) {
            if (request.getMinLineNumber() > request.getMaxLineNumber()) {
                throw new InvalidQueryException("minLineNumber must be <= maxLineNumber");
            }
        }
        
        // Validate sort field
        if (request.getSortBy() != null && !SORTABLE_FIELDS.contains(request.getSortBy())) {
            throw InvalidQueryException.invalidSortField(request.getSortBy());
        }
    }
    
    public Set<String> getSortableFields() {
        return SORTABLE_FIELDS;
    }
    
    public Set<String> getKeywordFields() {
        return KEYWORD_FIELDS;
    }
    
    public Set<String> getTextFields() {
        return TEXT_FIELDS;
    }
}
