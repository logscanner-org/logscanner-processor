package com.star.logscanner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadResponse {
    private String jobId;
    private String statusUrl;
    private String resultUrl;
    private String fileName;
    private long fileSize;
}