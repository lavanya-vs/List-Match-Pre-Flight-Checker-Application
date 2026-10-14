package com.iqvia.preflight.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iqvia.preflight.config.PreflightProperties;
import com.iqvia.preflight.dto.ColumnMappingEntry;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChunkedUploadService {

    private final PreflightProperties properties;
    private final PreflightJobService jobService;
    private final ObjectMapper objectMapper;
    private final Path chunkRoot;
    private final Map<String, UploadSession> sessions = new ConcurrentHashMap<>();

    public ChunkedUploadService(
            PreflightProperties properties,
            @Lazy PreflightJobService jobService,
            ObjectMapper objectMapper
    ) throws IOException {
        this.properties = properties;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
        this.chunkRoot = Path.of(properties.getUploadDir(), "chunks").toAbsolutePath().normalize();
        Files.createDirectories(chunkRoot);
    }

    public Map<String, Object> init(String fileName, long fileSize, Long requestedChunkSize) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new IOException("File name is required");
        }
        if (fileSize <= 0) {
            throw new IOException("File size must be positive");
        }
        long chunkSize = requestedChunkSize != null && requestedChunkSize > 0
                ? requestedChunkSize
                : properties.getChunkSizeBytes();
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

        String uploadId = UUID.randomUUID().toString();
        Path sessionDir = chunkRoot.resolve(uploadId);
        Files.createDirectories(sessionDir);

        UploadSession session = new UploadSession();
        session.uploadId = uploadId;
        session.fileName = fileName;
        session.fileSize = fileSize;
        session.chunkSize = chunkSize;
        session.totalChunks = totalChunks;
        session.sessionDir = sessionDir;
        session.receivedChunks = ConcurrentHashMap.newKeySet();
        session.createdAt = Instant.now();
        sessions.put(uploadId, session);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uploadId", uploadId);
        body.put("chunkSize", chunkSize);
        body.put("totalChunks", totalChunks);
        return body;
    }

    public Map<String, Object> uploadChunk(String uploadId, int chunkIndex, InputStream data) throws IOException {
        UploadSession session = requireSession(uploadId);
        if (chunkIndex < 0 || chunkIndex >= session.totalChunks) {
            throw new IOException("Invalid chunk index: " + chunkIndex);
        }
        Path chunkPath = session.sessionDir.resolve(String.format("%08d.part", chunkIndex));
        try (OutputStream out = Files.newOutputStream(chunkPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            data.transferTo(out);
        }
        session.receivedChunks.add(chunkIndex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uploadId", uploadId);
        body.put("chunkIndex", chunkIndex);
        body.put("receivedChunks", session.receivedChunks.size());
        body.put("totalChunks", session.totalChunks);
        body.put("complete", session.receivedChunks.size() == session.totalChunks);
        return body;
    }

    public Map<String, String> complete(String uploadId, boolean saveHistory, String columnMappingsJson)
            throws IOException {
        UploadSession session = requireSession(uploadId);
        if (session.receivedChunks.size() != session.totalChunks) {
            throw new IOException("Missing chunks: received "
                    + session.receivedChunks.size() + " of " + session.totalChunks);
        }

        String jobId = UUID.randomUUID().toString();
        Path merged = Path.of(properties.getUploadDir()).toAbsolutePath().normalize()
                .resolve(jobId + "_" + sanitize(session.fileName));
        Files.createDirectories(merged.getParent());

        try (OutputStream out = Files.newOutputStream(merged, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (int i = 0; i < session.totalChunks; i++) {
                Path part = session.sessionDir.resolve(String.format("%08d.part", i));
                Files.copy(part, out);
            }
        }

        List<ColumnMappingEntry> mappings = parseMappings(columnMappingsJson);
        cleanupSession(session);
        return jobService.submitFromPath(merged, session.fileName, Files.size(merged), saveHistory, mappings);
    }

    private List<ColumnMappingEntry> parseMappings(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ColumnMappingEntry>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    public void abort(String uploadId) {
        UploadSession session = sessions.remove(uploadId);
        if (session != null) {
            cleanupSession(session);
        }
    }

    private UploadSession requireSession(String uploadId) throws IOException {
        UploadSession session = sessions.get(uploadId);
        if (session == null) {
            throw new IOException("Upload session not found or expired: " + uploadId);
        }
        return session;
    }

    private void cleanupSession(UploadSession session) {
        sessions.remove(session.uploadId);
        try {
            if (Files.exists(session.sessionDir)) {
                try (var stream = Files.list(session.sessionDir)) {
                    stream.forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
                }
                Files.deleteIfExists(session.sessionDir);
            }
        } catch (IOException ignored) {
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static class UploadSession {
        String uploadId;
        String fileName;
        long fileSize;
        long chunkSize;
        int totalChunks;
        Path sessionDir;
        Set<Integer> receivedChunks;
        Instant createdAt;
    }
}
