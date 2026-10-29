package com.smallpict.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class JobStatusResult {
    @JsonProperty("job_id")
    private final String jobId;
    private final String status;
    private final String url;
    private final String format;
    @JsonProperty("bytes_saved")
    private final Long bytesSaved;
    private final Map<String, Object> error;
    @JsonProperty("created_at")
    private final String createdAt;
    @JsonProperty("updated_at")
    private final String updatedAt;
    @JsonProperty("mode_applied")
    private final String modeApplied;
    @JsonProperty("quality_applied")
    private final Integer qualityApplied;
    private final String folder;

    public JobStatusResult(String jobId, String status, String url, String format, Long bytesSaved,
            Map<String, Object> error, String createdAt, String updatedAt) {
        this(jobId, status, url, format, bytesSaved, error, createdAt, updatedAt, null, null);
    }

    public JobStatusResult(String jobId, String status, String url, String format, Long bytesSaved,
            Map<String, Object> error, String createdAt, String updatedAt, String modeApplied, Integer qualityApplied) {
        this(jobId, status, url, format, bytesSaved, error, createdAt, updatedAt, modeApplied, qualityApplied, null);
    }

    @JsonCreator
    public JobStatusResult(
            @JsonProperty("job_id") String jobId,
            @JsonProperty("status") String status,
            @JsonProperty("url") String url,
            @JsonProperty("format") String format,
            @JsonProperty("bytes_saved") Long bytesSaved,
            @JsonProperty("error") Map<String, Object> error,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("updated_at") String updatedAt,
            @JsonProperty("mode_applied") String modeApplied,
            @JsonProperty("quality_applied") Integer qualityApplied,
            @JsonProperty("folder") String folder) {
        this.jobId = jobId;
        this.status = status != null ? status : "processing";
        this.url = url;
        this.format = format;
        this.bytesSaved = bytesSaved;
        this.error = error;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.modeApplied = modeApplied;
        this.qualityApplied = qualityApplied;
        this.folder = folder;
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

    public Long getBytesSaved() {
        return bytesSaved;
    }

    public Map<String, Object> getError() {
        return error;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    /** Mode the server applied, as sent (a String so new values never break parsing); {@code null} for jobs created before modes existed. */
    public String getModeApplied() {
        return modeApplied;
    }

    /** Quality the encoder got; {@code null} when the server did not report it. */
    public Integer getQualityApplied() {
        return qualityApplied;
    }

    /** Folder stored with the job, normalized (lower case); {@code null} for jobs without one. */
    public String getFolder() {
        return folder;
    }
}
