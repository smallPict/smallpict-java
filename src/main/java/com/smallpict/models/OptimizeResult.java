package com.smallpict.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OptimizeResult {
    @JsonProperty("job_id")
    private final String jobId;
    private final String status;
    private final String url;
    private final String format;
    @JsonProperty("original_size")
    private final long originalSize;
    @JsonProperty("compressed_size")
    private final long compressedSize;
    @JsonProperty("bytes_saved")
    private final long bytesSaved;
    @JsonProperty("savings_percentage")
    private final double savingsPercentage;
    @JsonProperty("upload_url")
    private final String uploadUrl;
    private final String folder;
    @JsonIgnore
    private final byte[] data;

    public OptimizeResult(String jobId, String status, String url, String format, Long originalSize,
            Long compressedSize, Long bytesSaved, Double savingsPercentage, String uploadUrl) {
        this(jobId, status, url, format, originalSize, compressedSize, bytesSaved, savingsPercentage, uploadUrl, (String) null);
    }

    @JsonCreator
    public OptimizeResult(
            @JsonProperty("job_id") String jobId,
            @JsonProperty("status") String status,
            @JsonProperty("url") String url,
            @JsonProperty("format") String format,
            @JsonProperty("original_size") Long originalSize,
            @JsonProperty("compressed_size") Long compressedSize,
            @JsonProperty("bytes_saved") Long bytesSaved,
            @JsonProperty("savings_percentage") Double savingsPercentage,
            @JsonProperty("upload_url") String uploadUrl,
            @JsonProperty("folder") String folder) {
        this.jobId = jobId != null ? jobId : "sync";
        this.status = status != null ? status : "completed";
        this.url = url != null ? url : "";
        this.format = format != null ? format : "auto";
        this.originalSize = originalSize != null ? originalSize : 0L;
        this.compressedSize = compressedSize != null ? compressedSize : this.originalSize;
        this.bytesSaved = bytesSaved != null ? bytesSaved : Math.max(0L, this.originalSize - this.compressedSize);
        if (savingsPercentage != null) {
            this.savingsPercentage = Math.round(savingsPercentage * 100.0) / 100.0;
        } else if (this.originalSize > 0) {
            this.savingsPercentage = Math.round(((double) this.bytesSaved / this.originalSize * 100.0) * 100.0) / 100.0;
        } else {
            this.savingsPercentage = 0.0;
        }
        this.uploadUrl = uploadUrl;
        this.folder = folder;
        this.data = null;
    }

    public OptimizeResult(
            String jobId,
            String status,
            String url,
            String format,
            long originalSize,
            long compressedSize,
            long bytesSaved,
            double savingsPercentage,
            String uploadUrl,
            byte[] data) {
        this.jobId = jobId;
        this.status = status;
        this.url = url;
        this.format = format;
        this.originalSize = originalSize;
        this.compressedSize = compressedSize;
        this.bytesSaved = bytesSaved;
        this.savingsPercentage = savingsPercentage;
        this.uploadUrl = uploadUrl;
        this.folder = null;
        this.data = data;
    }

    public String getJobId() {
        return jobId;
    }

    public String getStatus() {
        return status;
    }

    public String getUrl() {
        return url;
    }

    public String getFormat() {
        return format;
    }

    public long getOriginalSize() {
        return originalSize;
    }

    public long getCompressedSize() {
        return compressedSize;
    }

    public long getBytesSaved() {
        return bytesSaved;
    }

    public double getSavingsPercentage() {
        return savingsPercentage;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    /** Folder stored with the job, normalized (lower case); {@code null} without one. */
    public String getFolder() {
        return folder;
    }

    public byte[] getData() {
        return data;
    }
}
