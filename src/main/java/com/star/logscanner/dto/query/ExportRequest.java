package com.star.logscanner.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Log export request")
public class ExportRequest {

    @Schema(description = "Query filters")
    private LogQueryRequest query;

    @Schema(description = "Export format", defaultValue = "CSV")
    @Builder.Default
    private ExportFormat format = ExportFormat.CSV;

    @Schema(description = "Fields to export")
    private List<String> fields;

    @Max(value = 100000, message = "Export limit cannot exceed 100000 records")
    @Schema(description = "Maximum records to export", defaultValue = "10000")
    @Builder.Default
    private Integer maxRecords = 10000;

    @Schema(description = "Include headers in CSV", defaultValue = "true")
    @Builder.Default
    private Boolean includeHeaders = true;

    @Schema(description = "CSV delimiter", defaultValue = ",")
    @Builder.Default
    private String delimiter = ",";

    public enum ExportFormat {
        CSV,
        JSON,
        NDJSON
    }

    public List<String> getEffectiveFields() {
        if (fields == null || fields.isEmpty()) {
            return List.of(
                    "timestamp",
                    "level",
                    "logger",
                    "thread",
                    "message",
                    "lineNumber",
                    "fileName"
            );
        }
        return fields;
    }
}
