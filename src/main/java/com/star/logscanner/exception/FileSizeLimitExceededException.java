package com.star.logscanner.exception;

public class FileSizeLimitExceededException extends FileUploadException {
    private final long maxSize;
    private final long actualSize;

    public FileSizeLimitExceededException(long maxSize, long actualSize) {
        super(String.format("File size exceeds limit. Maximum: %d bytes, Actual: %d bytes",
                maxSize, actualSize));
        this.maxSize = maxSize;
        this.actualSize = actualSize;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public long getActualSize() {
        return actualSize;
    }
}
