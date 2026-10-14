package com.iqvia.preflight.controller;

import com.iqvia.preflight.config.PreflightProperties;
import com.iqvia.preflight.dto.AdExportClashCheckRequest;
import com.iqvia.preflight.dto.AdExportClashCheckResponse;
import com.iqvia.preflight.dto.CheckHistoryItem;
import com.iqvia.preflight.dto.HeadersPreviewResponse;
import com.iqvia.preflight.dto.JobStatusResponse;
import com.iqvia.preflight.dto.PreflightCheckResponse;
import com.iqvia.preflight.service.PreflightJobService;
import com.iqvia.preflight.service.PreflightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PreflightController {

    private static final Logger LOG = LoggerFactory.getLogger(PreflightController.class);

    private final PreflightService preflightService;
    private final PreflightJobService jobService;
    private final PreflightProperties preflightProperties;

    public PreflightController(
            PreflightService preflightService,
            PreflightJobService jobService,
            PreflightProperties preflightProperties
    ) {
        this.preflightService = preflightService;
        this.jobService = jobService;
        this.preflightProperties = preflightProperties;
    }

    @PostMapping(value = "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> checkFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "saveHistory", defaultValue = "true") boolean saveHistory,
            @RequestParam(value = "async", defaultValue = "true") boolean async,
            @RequestParam(value = "columnMappings", required = false) String columnMappingsJson
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a file to upload."));
        }
        try {
            java.util.List<com.iqvia.preflight.dto.ColumnMappingEntry> userMappings =
                    preflightService.parseColumnMappings(columnMappingsJson);
            if (jobService.shouldRunAsync(file, async)) {
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(jobService.submit(file, saveHistory, userMappings));
            }
            PreflightCheckResponse response = preflightService.check(file, saveHistory, userMappings);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not read the file: " + e.getMessage()));
        } catch (RuntimeException e) {
            LOG.error("Unexpected pre-flight failure for {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "The file could not be analyzed. Please try again or contact support."));
        }
    }

    @PostMapping(value = "/headers/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> previewHeaders(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a file to upload."));
        }
        try {
            HeadersPreviewResponse response = preflightService.previewHeaders(file);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not read the file: " + e.getMessage()));
        }
    }

    @PostMapping("/ad-export-clash-check")
    public ResponseEntity<AdExportClashCheckResponse> adExportClashCheck(
            @RequestBody AdExportClashCheckRequest request
    ) {
        if (request.getColumnMappings() == null || request.getColumnMappings().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(preflightService.checkAdExportClashes(request.getColumnMappings()));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJob(@PathVariable String jobId) {
        try {
            return ResponseEntity.ok(jobService.getJob(jobId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/history")
    public List<CheckHistoryItem> getHistory() {
        return preflightService.getRecentHistory();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("asyncThresholdBytes", preflightProperties.getAsyncThresholdBytes());
        body.put("chunkUploadThresholdBytes", preflightProperties.getChunkUploadThresholdBytes());
        body.put("chunkSizeBytes", preflightProperties.getChunkSizeBytes());
        body.put("analysisSource", "Java rules and built-in summary templates");
        return body;
    }
}
