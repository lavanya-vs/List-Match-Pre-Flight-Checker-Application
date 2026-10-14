package com.iqvia.preflight.entity;

import com.iqvia.preflight.model.JobStatus;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "check_jobs")
public class CheckJob {

    @Id
    private String id;

    private String fileName;
    private long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.PENDING;

    private int progress;
    private String message;

    @Column(columnDefinition = "CLOB")
    private String resultJson;

    @Column(length = 2000)
    private String errorMessage;

    private String storedPath;

    @Column(columnDefinition = "CLOB")
    private String columnMappingsJson;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }

    public String getColumnMappingsJson() { return columnMappingsJson; }
    public void setColumnMappingsJson(String columnMappingsJson) { this.columnMappingsJson = columnMappingsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
