package com.star.logscanner.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "logscanner-logs")
public class LogEntry {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String jobId;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @Field(type = FieldType.Keyword)
    private String level;  // ERROR, WARN, INFO, DEBUG, TRACE

    @Field(type = FieldType.Text, analyzer = "standard")
    private String message;

    @Field(type = FieldType.Keyword)
    private String logger;  // Logger name/class

    @Field(type = FieldType.Keyword)
    private String thread;  // Thread name

    @Field(type = FieldType.Keyword)
    private String source;  // Source file/service

    @Field(type = FieldType.Long)
    private Long lineNumber;  // Original line number in file

    @Field(type = FieldType.Text)
    private String rawLine;  // Original raw log line

    @Field(type = FieldType.Keyword)
    private String fileName;  // Original file name

    @Field(type = FieldType.Object, enabled = false)
    private Map<String, Object> metadata;  // Additional parsed fields

    @Field(type = FieldType.Text, analyzer = "standard")
    private String stackTrace;  // For error logs with stack traces

    @Field(type = FieldType.Keyword)
    private String hostname;  // Server/container hostname

    @Field(type = FieldType.Keyword)
    private String application;  // Application name if present

    @Field(type = FieldType.Keyword)
    private String environment;  // Environment (dev, staging, prod)

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS||yyyy-MM-dd'T'HH:mm:ss||yyyy-MM-dd||epoch_millis")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS", timezone = "UTC")
    private java.time.Instant indexedAt;  // When this entry was indexed

    // Computed fields for analysis
    @Field(type = FieldType.Boolean)
    private Boolean hasError;

    @Field(type = FieldType.Boolean)
    private Boolean hasStackTrace;

    @Field(type = FieldType.Keyword)
    private String[] tags;  // Custom tags for categorization
}
