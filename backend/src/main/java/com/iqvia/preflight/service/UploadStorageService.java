package com.iqvia.preflight.service;

import com.iqvia.preflight.config.PreflightProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class UploadStorageService {

    private final Path uploadDir;

    public UploadStorageService(PreflightProperties properties) throws IOException {
        this.uploadDir = Path.of(properties.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    public StoredUpload store(MultipartFile file, String jobId) throws IOException {
        String originalName = file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename();
        Path target = uploadDir.resolve(jobId + "_" + sanitize(originalName));
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredUpload(target, originalName, Files.size(target));
    }

    public void deleteQuietly(String storedPath) {
        if (storedPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(storedPath));
        } catch (IOException ignored) {
            // Best-effort cleanup after processing.
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static class StoredUpload {
        public final Path path;
        public final String originalName;
        public final long sizeBytes;

        public StoredUpload(Path path, String originalName, long sizeBytes) {
            this.path = path;
            this.originalName = originalName;
            this.sizeBytes = sizeBytes;
        }
    }
}
