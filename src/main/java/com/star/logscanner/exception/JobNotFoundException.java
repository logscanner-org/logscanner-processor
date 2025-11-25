package com.star.logscanner.exception;

public class JobNotFoundException extends RuntimeException {
    private final String jobId;

    public JobNotFoundException(String jobId) {
        super(String.format("Job not found with ID: %s", jobId));
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }
}
