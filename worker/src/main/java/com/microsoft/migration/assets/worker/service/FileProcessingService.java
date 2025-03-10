package com.microsoft.migration.assets.worker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IMOperation;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import com.microsoft.migration.assets.worker.model.ImageProcessingMessage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static com.microsoft.migration.assets.worker.config.RabbitConfig.QUEUE_NAME;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {
    private final S3Client s3Client;
    
    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${local.storage.directory:storage}")
    private String storageDirectory;

    @RabbitListener(queues = QUEUE_NAME)
    public void processImage(ImageProcessingMessage message) {
        try {
            log.info("Processing image: {}", message.getKey());
            
            Path tempDir = Files.createTempDirectory("image-processing");
            Path originalFile = tempDir.resolve("original" + getExtension(message.getKey()));
            Path thumbnailFile = tempDir.resolve("thumbnail" + getExtension(message.getKey()));
            
            // Download original file
            if ("s3".equals(message.getStorageType())) {
                downloadFromS3(message.getKey(), originalFile);
            } else {
                downloadFromLocal(message.getKey(), originalFile);
            }

            // Generate thumbnail
            generateThumbnail(originalFile, thumbnailFile);

            // Upload thumbnail
            String thumbnailKey = getThumbnailKey(message.getKey());
            if ("s3".equals(message.getStorageType())) {
                uploadToS3(thumbnailFile, thumbnailKey, message.getContentType());
            } else {
                uploadToLocal(thumbnailFile, thumbnailKey);
            }

            // Cleanup
            Files.deleteIfExists(originalFile);
            Files.deleteIfExists(thumbnailFile);
            Files.deleteIfExists(tempDir);

            log.info("Successfully processed image: {}", message.getKey());
        } catch (Exception e) {
            log.error("Failed to process image: " + message.getKey(), e);
        }
    }

    private void generateThumbnail(Path input, Path output) throws Exception {
        IMOperation op = new IMOperation();
        op.addImage(input.toString());
        op.resize(150, 150, ">");  // Resize to max 150x150 while maintaining aspect ratio
        op.addImage(output.toString());
        
        ConvertCmd cmd = new ConvertCmd();
        cmd.run(op);
    }

    private void downloadFromS3(String key, Path destination) throws Exception {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
                
        try (var inputStream = s3Client.getObject(request)) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void uploadToS3(Path source, String key, String contentType) throws Exception {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();
                
        s3Client.putObject(request, RequestBody.fromFile(source));
    }

    private void downloadFromLocal(String key, Path destination) throws Exception {
        Path sourcePath = Paths.get(storageDirectory).resolve(key);
        Files.copy(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private void uploadToLocal(Path source, String key) throws Exception {
        Path destinationPath = Paths.get(storageDirectory).resolve(key);
        Files.copy(source, destinationPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private String getThumbnailKey(String key) {
        int dotIndex = key.lastIndexOf('.');
        if (dotIndex > 0) {
            return key.substring(0, dotIndex) + "_thumbnail" + key.substring(dotIndex);
        }
        return key + "_thumbnail";
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(dotIndex) : "";
    }
}