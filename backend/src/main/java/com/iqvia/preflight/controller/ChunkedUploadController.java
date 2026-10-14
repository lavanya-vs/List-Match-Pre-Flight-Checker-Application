package com.iqvia.preflight.controller;

import com.iqvia.preflight.dto.ChunkedUploadInitRequest;
import com.iqvia.preflight.service.ChunkedUploadService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
public class ChunkedUploadController {

    private final ChunkedUploadService chunkedUploadService;

    public ChunkedUploadController(ChunkedUploadService chunkedUploadService) {
        this.chunkedUploadService = chunkedUploadService;
    }

    @PostMapping("/init")
    public ResponseEntity<?> init(@RequestBody ChunkedUploadInitRequest request) {
        try {
            return ResponseEntity.ok(chunkedUploadService.init(
                    request.getFileName(),
                    request.getFileSize(),
                    request.getChunkSize()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping(value = "/{uploadId}/chunk/{chunkIndex}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> uploadChunk(
            @PathVariable String uploadId,
            @PathVariable int chunkIndex,
            @RequestBody byte[] chunkData
    ) {
        try {
            return ResponseEntity.ok(chunkedUploadService.uploadChunk(
                    uploadId, chunkIndex, new java.io.ByteArrayInputStream(chunkData)));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<?> complete(
            @PathVariable String uploadId,
            @RequestParam(value = "saveHistory", defaultValue = "true") boolean saveHistory,
            @RequestParam(value = "columnMappings", required = false) String columnMappingsJson
    ) {
        try {
            return ResponseEntity.accepted()
                    .body(chunkedUploadService.complete(uploadId, saveHistory, columnMappingsJson));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<?> abort(@PathVariable String uploadId) {
        chunkedUploadService.abort(uploadId);
        return ResponseEntity.noContent().build();
    }
}
