package com.microsoft.migration.assets.service;

import com.microsoft.migration.assets.model.S3Object;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Profile("dev") // Only active when dev profile is active
public class LocalFileStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileStorageService.class);
    
    @Value("${local.storage.directory:storage}")
    private String storageDirectory;
    
    private Path rootLocation;
    
    @PostConstruct
    public void init() throws IOException {
        rootLocation = Paths.get(storageDirectory).toAbsolutePath().normalize();
        logger.info("Local storage directory: {}", rootLocation);
        
        // Create directory if it doesn't exist
        if (!Files.exists(rootLocation)) {
            Files.createDirectories(rootLocation);
            logger.info("Created local storage directory");
        }
    }

    @Override
    public List<S3Object> listObjects() {
        try {
            return Files.walk(rootLocation, 1)
                .filter(path -> !path.equals(rootLocation))
                .map(path -> {
                    try {
                        String filename = path.getFileName().toString();
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        return new S3Object(
                            filename,
                            filename,
                            Files.size(path),
                            attrs.creationTime().toInstant(),
                            generateUrl(filename)
                        );
                    } catch (IOException e) {
                        logger.error("Failed to read file attributes", e);
                        return null;
                    }
                })
                .filter(s3Object -> s3Object != null)
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Failed to list files", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void uploadObject(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file");
        }
        
        String filename = StringUtils.cleanPath(file.getOriginalFilename());
        if (filename.contains("..")) {
            throw new IOException("Cannot store file with relative path outside current directory");
        }
        
        Path targetLocation = rootLocation.resolve(filename);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Stored file: {}", targetLocation);
    }

    @Override
    public InputStream getObject(String key) throws IOException {
        Path file = rootLocation.resolve(key);
        if (!Files.exists(file)) {
            throw new FileNotFoundException("File not found: " + key);
        }
        return new BufferedInputStream(Files.newInputStream(file));
    }

    @Override
    public void deleteObject(String key) throws IOException {
        Path file = rootLocation.resolve(key);
        if (!Files.exists(file)) {
            throw new FileNotFoundException("File not found: " + key);
        }
        Files.delete(file);
        logger.info("Deleted file: {}", file);
    }
    
    private String generateUrl(String key) {
        // Generate a URL for the object (simplified path)
        return "/s3/view/" + key;
    }
}