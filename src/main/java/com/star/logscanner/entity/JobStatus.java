package com.star.logscanner.entity;

import com.star.logscanner.dto.JobStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class JobStatus {
    private String jobId;
    private JobStatusEnum status;
    private int progress;
    private String message;
    private String error;
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
