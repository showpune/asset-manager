package com.microsoft.migration.assets.service;

import com.microsoft.migration.assets.model.ImageProcessingMessage;
import com.microsoft.migration.assets.model.S3Object;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static com.microsoft.migration.assets.config.RabbitConfig.QUEUE_NAME;

@Service
@RequiredArgsConstructor
@Profile("!dev") // Active when not in dev profile
public class AwsS3Service implements StorageService {

    private final S3Client s3Client;
    private final RabbitTemplate rabbitTemplate;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Override
    public List<S3Object> listObjects() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);

        return response.contents().stream()
                .map(s3Object -> new S3Object(
                        s3Object.key(),
                        extractFilename(s3Object.key()),
                        s3Object.size(),
                        s3Object.lastModified(),
                        generateUrl(s3Object.key())
                ))
                .collect(Collectors.toList());
    }

    @Override
    public void uploadObject(MultipartFile file) throws IOException {
        String key = file.getOriginalFilename();
        
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();
        
        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        // Send message to queue for thumbnail generation
        ImageProcessingMessage message = new ImageProcessingMessage(
            key,
            file.getContentType(),
            getStorageType(),
            file.getSize()
        );
        rabbitTemplate.convertAndSend(QUEUE_NAME, message);
    }

    @Override
    public InputStream getObject(String key) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        
        return s3Client.getObject(request);
    }

    @Override
    public void deleteObject(String key) throws IOException {
        // Delete both original and thumbnail if it exists
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        
        s3Client.deleteObject(request);

        try {
            // Try to delete thumbnail if it exists
            DeleteObjectRequest thumbnailRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(getThumbnailKey(key))
                    .build();
            s3Client.deleteObject(thumbnailRequest);
        } catch (Exception e) {
            // Ignore if thumbnail doesn't exist
        }
    }

    @Override
    public String getStorageType() {
        return "s3";
    }

    private String extractFilename(String key) {
        // Extract filename from the object key
        int lastSlashIndex = key.lastIndexOf('/');
        return lastSlashIndex >= 0 ? key.substring(lastSlashIndex + 1) : key;
    }
    
    private String generateUrl(String key) {
        // Generate a URL for the object (simplified, not a presigned URL)
        return "/s3/view/" + key;
    }
}