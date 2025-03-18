package com.microsoft.migration.assets.worker.service;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.microsoft.migration.assets.worker.model.ImageMetadata;
import com.microsoft.migration.assets.worker.repository.ImageMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Profile("!dev")
@RequiredArgsConstructor
public class S3FileProcessingService extends AbstractFileProcessingService {
    private final BlobServiceClient blobServiceClient;
    private final ImageMetadataRepository imageMetadataRepository;
    
    @Value("${azure.storage.blob.container-name}")
    private String containerName;

    @Override
    public void downloadOriginal(String key, Path destination) throws Exception {
        var blobClient = blobServiceClient.getBlobContainerClient(containerName).getBlobClient(key);
        try (var inputStream = blobClient.openInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void uploadThumbnail(Path source, String key, String contentType) throws Exception {
        var blobClient = blobServiceClient.getBlobContainerClient(containerName).getBlobClient(key);
        BlobHttpHeaders headers = new BlobHttpHeaders().setContentType(contentType);
        BlobParallelUploadOptions options = new BlobParallelUploadOptions(Files.newInputStream(source)).setHeaders(headers);
        blobClient.uploadWithResponse(options, null, null);
        
        // The key parameter is already the thumbnail key, so extract the original key
        String originalKey = extractOriginalKey(key);
        
        // Find metadata by S3 key instead of using it as ID
        ImageMetadata metadata = imageMetadataRepository.findByS3Key(originalKey)
            .orElseGet(() -> {
                ImageMetadata newMetadata = new ImageMetadata();
                newMetadata.setId(UUID.randomUUID().toString()); // Generate new UUID for ID
                newMetadata.setS3Key(originalKey);
                return newMetadata;
            });

        metadata.setThumbnailKey(key);
        metadata.setThumbnailUrl(generateUrl(key));
        imageMetadataRepository.save(metadata);
    }

    @Override
    public String getStorageType() {
        return "azure";
    }

    @Override
    protected String generateUrl(String key) {
        var blobClient = blobServiceClient.getBlobContainerClient(containerName).getBlobClient(key);
        return blobClient.getBlobUrl();
    }

    private String extractOriginalKey(String key) {
        // Remove _thumbnail suffix if present
        String suffix = "_thumbnail";
        int suffixIndex = key.lastIndexOf(suffix);
        if (suffixIndex > 0) {
            return key.substring(0, suffixIndex);
        }
        return key;
    }
}
