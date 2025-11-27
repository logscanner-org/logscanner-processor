package com.star.logscanner.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.star.logscanner.dto.JobStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("JobStatus")
public class JobStatus implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String jobId;

    private JobStatusEnum status;

    private Integer progress;  // 0-100

    private String message;

    private String error;

    private String fileName;

    private Long fileSize;

    private String timestampFormat;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime completedAt;

    // Processing statistics
    private Long totalLines;
    private Long processedLines;
    private Long successfulLines;
    private Long failedLines;

    // Performance metrics
    private Long processingTimeMs;
    private Double linesPerSecond;

    // TTL - 24 hours in seconds
    @TimeToLive(unit = TimeUnit.HOURS)
    private Long ttl = 24L;

    public JobStatus(String jobId, JobStatusEnum status, int progress,
                     String message, String error, LocalDateTime startedAt,
                     LocalDateTime updatedAt, LocalDateTime completedAt) {
        this.jobId = jobId;
        this.status = status;
        this.progress = progress;
        this.message = message;
        this.error = error;
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
        this.ttl = 24L;
    }
}
