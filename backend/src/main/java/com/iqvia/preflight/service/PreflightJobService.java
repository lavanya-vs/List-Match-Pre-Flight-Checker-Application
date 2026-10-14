package com.iqvia.preflight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iqvia.preflight.config.PreflightProperties;
import com.iqvia.preflight.dto.ColumnMappingEntry;
import com.iqvia.preflight.dto.JobStatusResponse;
import com.iqvia.preflight.dto.PreflightCheckResponse;
import com.iqvia.preflight.entity.CheckJob;
import com.iqvia.preflight.model.JobStatus;
import com.iqvia.preflight.repository.CheckJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PreflightJobService {

    private static final Logger LOG = LoggerFactory.getLogger(PreflightJobService.class);

    private final CheckJobRepository jobRepository;
    private final UploadStorageService storageService;
    private final PreflightService preflightService;
    private final PreflightProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public PreflightJobService(
            CheckJobRepository jobRepository,
            UploadStorageService storageService,
            PreflightService preflightService,
            PreflightProperties properties,
            ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.storageService = storageService;
        this.preflightService = preflightService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean shouldRunAsync(MultipartFile file, boolean asyncRequested) {
        return asyncRequested || file.getSize() >= properties.getAsyncThresholdBytes();
    }

    public Map<String, String> submitFromPath(
            java.nio.file.Path path,
            String fileName,
            long sizeBytes,
            boolean saveHistory
    ) {
        return submitFromPath(path, fileName, sizeBytes, saveHistory, null);
    }

    public Map<String, String> submitFromPath(
            java.nio.file.Path path,
            String fileName,
            long sizeBytes,
            boolean saveHistory,
            List<ColumnMappingEntry> userMappings
    ) {
        String jobId = UUID.randomUUID().toString();
        CheckJob job = new CheckJob();
        job.setId(jobId);
        job.setFileName(fileName);
        job.setFileSizeBytes(sizeBytes);
        job.setStatus(JobStatus.PENDING);
        job.setProgress(0);
        job.setMessage("Upload complete — queued for analysis");
        job.setStoredPath(path.toAbsolutePath().normalize().toString());
        if (userMappings != null && !userMappings.isEmpty()) {
            try {
                job.setColumnMappingsJson(objectMapper.writeValueAsString(userMappings));
            } catch (Exception e) {
                LOG.warn("Could not serialize column mappings for job {}", jobId);
            }
        }
        jobRepository.save(job);

        executor.submit(() -> runJob(jobId, saveHistory));
        return Map.of("jobId", jobId, "status", JobStatus.PENDING.name());
    }

    public Map<String, String> submit(MultipartFile file, boolean saveHistory) throws IOException {
        return submit(file, saveHistory, null);
    }

    public Map<String, String> submit(
            MultipartFile file,
            boolean saveHistory,
            List<ColumnMappingEntry> userMappings
    ) throws IOException {
        String jobId = UUID.randomUUID().toString();
        UploadStorageService.StoredUpload stored = storageService.store(file, jobId);

        CheckJob job = new CheckJob();
        job.setId(jobId);
        job.setFileName(stored.originalName);
        job.setFileSizeBytes(stored.sizeBytes);
        job.setStatus(JobStatus.PENDING);
        job.setProgress(0);
        job.setMessage("Upload received");
        job.setStoredPath(stored.path.toString());
        if (userMappings != null && !userMappings.isEmpty()) {
            job.setColumnMappingsJson(objectMapper.writeValueAsString(userMappings));
        }
        jobRepository.save(job);

        executor.submit(() -> runJob(jobId, saveHistory));
        return Map.of("jobId", jobId, "status", JobStatus.PENDING.name());
    }

    public JobStatusResponse getJob(String jobId) {
        CheckJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        PreflightCheckResponse result = null;
        if (job.getStatus() == JobStatus.COMPLETE && job.getResultJson() != null) {
            try {
                result = objectMapper.readValue(job.getResultJson(), PreflightCheckResponse.class);
            } catch (Exception e) {
                LOG.warn("Could not deserialize job result {}", jobId);
            }
        }
        return new JobStatusResponse(
                job.getId(),
                job.getStatus(),
                job.getProgress(),
                job.getMessage(),
                result,
                job.getStatus() == JobStatus.COMPLETE ? job.getResultJson() : null,
                job.getErrorMessage()
        );
    }

    private void runJob(String jobId, boolean saveHistory) {
        CheckJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        try {
            updateJob(job, JobStatus.RUNNING, 10, "Reading file");
            List<ColumnMappingEntry> mappings = parseJobMappings(job.getColumnMappingsJson());
            PreflightCheckResponse response = preflightService.checkStoredFile(
                    job.getStoredPath(),
                    job.getFileName(),
                    saveHistory,
                    progress -> updateProgress(jobId, progress),
                    mappings
            );
            job = jobRepository.findById(jobId).orElseThrow();
            job.setResultJson(objectMapper.writeValueAsString(response));
            job.setStatus(JobStatus.COMPLETE);
            job.setProgress(100);
            job.setMessage("Analysis complete");
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);
        } catch (Exception ex) {
            LOG.error("Job {} failed", jobId, ex);
            job = jobRepository.findById(jobId).orElse(null);
            if (job != null) {
                job.setStatus(JobStatus.FAILED);
                job.setErrorMessage(ex.getMessage() == null ? "Processing failed" : ex.getMessage());
                job.setMessage("Failed");
                job.setUpdatedAt(Instant.now());
                jobRepository.save(job);
            }
        } finally {
            CheckJob finalJob = jobRepository.findById(jobId).orElse(null);
            if (finalJob != null) {
                storageService.deleteQuietly(finalJob.getStoredPath());
            }
        }
    }

    private void updateProgress(String jobId, int progress) {
        jobRepository.findById(jobId).ifPresent(job -> updateJob(job, JobStatus.RUNNING, progress, progressMessage(progress)));
    }

    private void updateJob(CheckJob job, JobStatus status, int progress, String message) {
        job.setStatus(status);
        job.setProgress(progress);
        job.setMessage(message);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
    }

    private List<ColumnMappingEntry> parseJobMappings(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return preflightService.parseColumnMappings(json);
        } catch (Exception e) {
            LOG.warn("Could not parse column mappings for job: {}", e.getMessage());
            return null;
        }
    }

    private String progressMessage(int progress) {
        if (progress < 30) {
            return "Reading file";
        }
        if (progress < 70) {
            return "Scoring match paths";
        }
        if (progress < 95) {
            return "Building analysis";
        }
        return "Finishing";
    }
}
