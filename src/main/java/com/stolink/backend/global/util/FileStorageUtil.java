package com.stolink.backend.global.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Component
public class FileStorageUtil {

    @Value("${app.storage.base-path}")
    private String basePath;

    public String store(MultipartFile file, String subDirectory) {
        try {
            // Create directory if not exists
            Path uploadPath = Paths.get(basePath, subDirectory);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String filename = UUID.randomUUID().toString() + extension;

            // Save file
            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("File stored: {}", filePath);
            return "/" + subDirectory + "/" + filename;

        } catch (IOException e) {
            log.error("Failed to store file", e);
            throw new RuntimeException("파일 저장에 실패했습니다.", e);
        }
    }

    public void delete(String filePath) {
        try {
            Path path = Paths.get(basePath, filePath);
            Files.deleteIfExists(path);
            log.info("File deleted: {}", path);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePath, e);
        }
    }
}
