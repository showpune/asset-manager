package com.microsoft.migration.assets.worker.service;

import com.microsoft.migration.assets.worker.model.ImageProcessingMessage;
import lombok.extern.slf4j.Slf4j;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IMOperation;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.microsoft.migration.assets.worker.config.RabbitConfig.QUEUE_NAME;

@Slf4j
public abstract class AbstractFileProcessingService implements FileProcessor {
    
    @RabbitListener(queues = QUEUE_NAME)
    public void processImage(ImageProcessingMessage message) {
        try {
            log.info("Processing image: {}", message.getKey());
            
            Path tempDir = Files.createTempDirectory("image-processing");
            Path originalFile = tempDir.resolve("original" + getExtension(message.getKey()));
            Path thumbnailFile = tempDir.resolve("thumbnail" + getExtension(message.getKey()));
            
            // Only process if message matches our storage type
            if (message.getStorageType().equals(getStorageType())) {
                // Download original file
                downloadOriginal(message.getKey(), originalFile);
                
                // Generate thumbnail
                generateThumbnail(originalFile, thumbnailFile);

                // Upload thumbnail
                String thumbnailKey = getThumbnailKey(message.getKey());
                uploadThumbnail(thumbnailFile, thumbnailKey, message.getContentType());

                log.info("Successfully processed image: {}", message.getKey());
            } else {
                log.debug("Skipping message with storage type: {} (we handle {})", 
                    message.getStorageType(), getStorageType());
            }

            // Cleanup
            Files.deleteIfExists(originalFile);
            Files.deleteIfExists(thumbnailFile);
            Files.deleteIfExists(tempDir);
        } catch (Exception e) {
            log.error("Failed to process image: " + message.getKey(), e);
        }
    }

    protected void generateThumbnail(Path input, Path output) throws Exception {
        IMOperation op = new IMOperation();
        op.addImage(input.toString());
        op.resize(150, 150, ">");  // Resize to max 150x150 while maintaining aspect ratio
        op.addImage(output.toString());
        
        ConvertCmd cmd = new ConvertCmd();
        cmd.run(op);
    }

    protected String getThumbnailKey(String key) {
        int dotIndex = key.lastIndexOf('.');
        if (dotIndex > 0) {
            return key.substring(0, dotIndex) + "_thumbnail" + key.substring(dotIndex);
        }
        return key + "_thumbnail";
    }

    protected String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(dotIndex) : "";
    }
}