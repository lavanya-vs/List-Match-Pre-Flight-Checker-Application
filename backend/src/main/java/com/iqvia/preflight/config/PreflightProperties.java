package com.iqvia.preflight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "preflight")
public class PreflightProperties {

    private long asyncThresholdBytes = 10 * 1024 * 1024;
    private long chunkUploadThresholdBytes = 50 * 1024 * 1024;
    private long chunkSizeBytes = 8 * 1024 * 1024;
    private String uploadDir = "./data/uploads";

    public long getAsyncThresholdBytes() {
        return asyncThresholdBytes;
    }

    public void setAsyncThresholdBytes(long asyncThresholdBytes) {
        this.asyncThresholdBytes = asyncThresholdBytes;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public long getChunkUploadThresholdBytes() {
        return chunkUploadThresholdBytes;
    }

    public void setChunkUploadThresholdBytes(long chunkUploadThresholdBytes) {
        this.chunkUploadThresholdBytes = chunkUploadThresholdBytes;
    }

    public long getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public void setChunkSizeBytes(long chunkSizeBytes) {
        this.chunkSizeBytes = chunkSizeBytes;
    }
}
