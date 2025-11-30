package com.star.logscanner.dto.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Timeline data for log visualization")
public class TimelineData {

    @Schema(description = "Job ID")
    private String jobId;

    @Schema(description = "Time interval", example = "1h")
    private String interval;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Schema(description = "Start of time range")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Schema(description = "End of time range")
    private LocalDateTime endTime;

    @Schema(description = "Total logs in range")
    private Long totalCount;

    @Schema(description = "Timeline data points")
    private List<TimelineBucket> buckets;

    @Schema(description = "Level breakdown by time")
    private List<LevelBreakdown> levelBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Timeline data point")
    public static class TimelineBucket {

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(description = "Bucket start time")
        private LocalDateTime timestamp;

        @Schema(description = "Log count")
        private long count;

        @Schema(description = "Error count")
        private long errorCount;

        @Schema(description = "Warning count")
        private long warningCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Level breakdown for time bucket")
    public static class LevelBreakdown {

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(description = "Bucket start time")
        private LocalDateTime timestamp;

        @Schema(description = "Count per level")
        private Map<String, Long> levels;
    }

    @Getter
    public enum Interval {
        SECOND("1s"),
        MINUTE("1m"),
        FIVE_MINUTES("5m"),
        FIFTEEN_MINUTES("15m"),
        THIRTY_MINUTES("30m"),
        HOUR("1h"),
        DAY("1d"),
        WEEK("1w"),
        MONTH("1M");
        
        private final String value;
        
        Interval(String value) {
            this.value = value;
        }

        public static Interval parse(String value) {
            if (value == null) return HOUR;
            
            for (Interval interval : values()) {
                if (interval.value.equalsIgnoreCase(value)) {
                    return interval;
                }
            }
            
            // Default to hour for unknown values
            return HOUR;
        }

        public String toElasticsearchInterval() {
            return switch (this) {
                case SECOND -> "1s";
                case MINUTE -> "1m";
                case FIVE_MINUTES -> "5m";
                case FIFTEEN_MINUTES -> "15m";
                case THIRTY_MINUTES -> "30m";
                case HOUR -> "1h";
                case DAY -> "1d";
                case WEEK -> "1w";
                case MONTH -> "1M";
            };
        }
    }
}
