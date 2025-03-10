package com.microsoft.migration.assets.worker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@Profile("dev")
public class LocalFileProcessingService extends AbstractFileProcessingService {
    
    @Value("${local.storage.directory:storage}")
    private String storageDirectory;
    
    private Path rootLocation;
    
    @PostConstruct
    public void init() throws Exception {
        rootLocation = Paths.get(storageDirectory).toAbsolutePath().normalize();
        if (!Files.exists(rootLocation)) {
            Files.createDirectories(rootLocation);
        }
    }

    @Override
    public void downloadOriginal(String key, Path destination) throws Exception {
        Path sourcePath = rootLocation.resolve(key);
        Files.copy(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void uploadThumbnail(Path source, String key, String contentType) throws Exception {
        Path destinationPath = rootLocation.resolve(key);
        Files.createDirectories(destinationPath.getParent());
        Files.copy(source, destinationPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public String getStorageType() {
        return "local";
    }
}