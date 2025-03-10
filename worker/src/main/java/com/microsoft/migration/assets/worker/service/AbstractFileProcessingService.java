package com.microsoft.migration.assets.worker.service;

import com.microsoft.migration.assets.worker.model.ImageProcessingMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
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

    protected void generateThumbnail(Path input, Path output) throws IOException {
        log.info("Generating thumbnail for: {}", input);

        // Read the original image
        BufferedImage originalImage = ImageIO.read(input.toFile());
        if (originalImage == null) {
            throw new IOException("Could not read image file: " + input);
        }

        // Calculate thumbnail dimensions while preserving aspect ratio
        int thumbnailWidth = 150;
        int thumbnailHeight = 150;

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        double aspectRatio = (double) originalWidth / originalHeight;

        if (originalWidth > originalHeight) {
            thumbnailHeight = (int) (thumbnailWidth / aspectRatio);
        } else {
            thumbnailWidth = (int) (thumbnailHeight * aspectRatio);
        }

        // Create a new BufferedImage for the thumbnail
        BufferedImage thumbnailImage = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);

        // Set up the rendering process
        Graphics2D g2d = thumbnailImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw the scaled image
        g2d.drawImage(originalImage, 0, 0, thumbnailWidth, thumbnailHeight, null);
        g2d.dispose();

        // Determine the output format based on the file extension
        String extension = getExtension(output.toString());
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        if (extension.isEmpty()) {
            extension = "jpg"; // Default to jpg if no extension found
        }

        // Write the thumbnail to the output file
        ImageIO.write(thumbnailImage, extension, output.toFile());

        log.info("Successfully generated thumbnail: {}", output);
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
