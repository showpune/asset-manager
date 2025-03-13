package com.microsoft.migration.assets.worker.service;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@Profile("!dev")
@RequiredArgsConstructor
public class S3FileProcessingService extends AbstractFileProcessingService {
    private final BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
    
    @Value("${azure.blob.container}")
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
        var headers = new BlobHttpHeaders().setContentType(contentType);
        var options = new BlobParallelUploadOptions(Files.newInputStream(source)).setHeaders(headers);
        blobClient.uploadWithResponse(options, null, null);
    }

    @Override
    public String getStorageType() {
        return "azure_blob";
    }
}
