package com.star.logscanner.exception;

import lombok.Getter;

@Getter
public class FileSizeLimitExceededException extends FileUploadException {
    private final long maxSize;
    private final long actualSize;

    public FileSizeLimitExceededException(long maxSize, long actualSize) {
        super(String.format("File size exceeds limit. Maximum: %d bytes, Actual: %d bytes",
                maxSize, actualSize));
        this.maxSize = maxSize;
        this.actualSize = actualSize;
    }

}
