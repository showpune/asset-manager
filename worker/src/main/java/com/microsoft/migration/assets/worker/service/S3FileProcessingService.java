package com.microsoft.migration.assets.worker.service;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.microsoft.migration.assets.common.model.ImageMetadata;
import com.microsoft.migration.assets.common.repository.ImageMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@Profile("!dev")
@RequiredArgsConstructor
public class S3FileProcessingService extends AbstractFileProcessingService {
    private final BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
            .endpoint("https://<your-storage-account-name>.blob.core.windows.net")
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
    private final ImageMetadataRepository imageMetadataRepository;
    
    @Value("${azure.storage.container}")
    private String containerName;

    @Override
    public void downloadOriginal(String key, Path destination) throws Exception {
        var blobClient = blobServiceClient.getBlobContainerClient(containerName).getBlobClient(key);
        try (InputStream inputStream = blobClient.openInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void uploadThumbnail(Path source, String key, String contentType) throws Exception {
        var blobClient = blobServiceClient.getBlobContainerClient(containerName).getBlobClient(key);
        BlobHttpHeaders headers = new BlobHttpHeaders().setContentType(contentType);
        BlobParallelUploadOptions options = new BlobParallelUploadOptions(Files.newInputStream(source)).setHeaders(headers);
        blobClient.uploadWithResponse(options, null, null);
        
        // Save or update thumbnail metadata
        ImageMetadata metadata = imageMetadataRepository.findById(extractOriginalKey(key))
            .orElseGet(() -> {
                ImageMetadata newMetadata = new ImageMetadata();
                newMetadata.setId(extractOriginalKey(key));
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

    private String extractOriginalKey(String thumbnailKey) {
        // Remove "_thumbnail" from the key to get the original key
        int dotIndex = thumbnailKey.lastIndexOf('.');
        if (dotIndex > 0) {
            String nameWithoutExt = thumbnailKey.substring(0, dotIndex);
            String ext = thumbnailKey.substring(dotIndex);
            if (nameWithoutExt.endsWith("_thumbnail")) {
                return nameWithoutExt.substring(0, nameWithoutExt.length() - 10) + ext;
            }
        }
        return thumbnailKey;
    }
}