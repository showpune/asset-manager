package com.microsoft.migration.assets.service;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.microsoft.migration.assets.common.model.ImageMetadata;
import com.microsoft.migration.assets.common.model.ImageProcessingMessage;
import com.microsoft.migration.assets.common.repository.ImageMetadataRepository;
import com.microsoft.migration.assets.model.S3Object;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.microsoft.migration.assets.config.RabbitConfig.QUEUE_NAME;

@Service
@RequiredArgsConstructor
@Profile("!dev") // Active when not in dev profile
public class AwsS3Service implements StorageService {

    private final BlobServiceClient blobServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final ImageMetadataRepository imageMetadataRepository;

    @Value("${azure.storage.container}")
    private String containerName;


    public List<BlobItem> listObjects() {
        ListBlobsOptions options = new ListBlobsOptions().setDetails(new BlobListDetails().setRetrieveMetadata(true));
        return blobServiceClient.getBlobContainerClient(containerName).listBlobs(options, null)
                .stream()
                .map(blobItem -> new S3Object(
                        blobItem.getName(),
                        extractFilename(blobItem.getName()),
                        blobItem.getProperties().getContentLength(),
                        blobItem.getProperties().getLastModified().toInstant(),
                        generateUrl(blobItem.getName())
                ))
                .collect(Collectors.toList());
    }

    @Override
    public void uploadObject(MultipartFile file) throws IOException {
        String key = generateKey(file.getOriginalFilename());

        blobServiceClient.getBlobContainerClient(containerName)
                .getBlobClient(key)
                .upload(file.getInputStream(), file.getSize(), true);

        // Send message to queue for thumbnail generation
        ImageProcessingMessage message = new ImageProcessingMessage(
                key,
                file.getContentType(),
                getStorageType(),
                file.getSize()
        );
        rabbitTemplate.convertAndSend(QUEUE_NAME, message);

        // Create and save metadata to database
        ImageMetadata metadata = new ImageMetadata();
        metadata.setId(UUID.randomUUID().toString());
        metadata.setFilename(file.getOriginalFilename());
        metadata.setContentType(file.getContentType());
        metadata.setSize(file.getSize());
        metadata.setS3Key(key);
        metadata.setS3Url(generateUrl(key));

        imageMetadataRepository.save(metadata);
    }

    @Override
    public InputStream getObject(String key) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blobServiceClient.getBlobContainerClient(containerName)
                .getBlobClient(key)
                .downloadStream(outputStream);
        return outputStream;
    }

    @Override
    public void deleteObject(String key) throws IOException {
        // Delete both original and thumbnail if it exists
        blobServiceClient.getBlobContainerClient(containerName)
                .getBlobClient(key)
                .delete();

        try {
            // Try to delete thumbnail if it exists
            blobServiceClient.getBlobContainerClient(containerName)
                    .getBlobClient(getThumbnailKey(key))
                    .delete();
        } catch (Exception e) {
            // Ignore if thumbnail doesn't exist
        }

        // Delete metadata from database
        imageMetadataRepository.findAll().stream()
                .filter(metadata -> metadata.getS3Key().equals(key))
                .findFirst()
                .ifPresent(metadata -> imageMetadataRepository.delete(metadata));
    }

    @Override
    public String getStorageType() {
        return "azure";
    }

    private String extractFilename(String key) {
        // Extract filename from the object key
        int lastSlashIndex = key.lastIndexOf('/');
        return lastSlashIndex >= 0 ? key.substring(lastSlashIndex + 1) : key;
    }

    private String generateUrl(String key) {
        return blobServiceClient.getBlobContainerClient(containerName)
                .getBlobClient(key)
                .getBlobUrl();
    }

    private String generateKey(String filename) {
        return UUID.randomUUID().toString() + "-" + filename;
    }
}