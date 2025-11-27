package com.star.logscanner.exception;

import lombok.Getter;

@Getter
public class JobNotFoundException extends RuntimeException {
    private final String jobId;

    public JobNotFoundException(String jobId) {
        super(String.format("Job not found with ID: %s", jobId));
        this.jobId = jobId;
    }

}
