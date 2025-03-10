package com.microsoft.migration.assets.worker.service;

import com.microsoft.migration.assets.worker.model.ImageProcessingMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.rabbitmq.client.Channel;

import static com.microsoft.migration.assets.worker.config.RabbitConfig.QUEUE_NAME;

@Slf4j
public abstract class AbstractFileProcessingService implements FileProcessor {

    @RabbitListener(queues = QUEUE_NAME, ackMode = "MANUAL")
    public void processImage(ImageProcessingMessage message, Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        boolean processingSuccess = false;
        Path tempDir = null;
        Path originalFile = null;
        Path thumbnailFile = null;

        try {
            log.info("Processing image: {}", message.getKey());

            tempDir = Files.createTempDirectory("image-processing");
            originalFile = tempDir.resolve("original" + getExtension(message.getKey()));
            thumbnailFile = tempDir.resolve("thumbnail" + getExtension(message.getKey()));

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

                // Mark processing as successful
                processingSuccess = true;
            } else {
                log.debug("Skipping message with storage type: {} (we handle {})",
                    message.getStorageType(), getStorageType());
                // This is not an error, just not for this service, so we can acknowledge
                processingSuccess = true;
            }
        } catch (Exception e) {
            log.error("Failed to process image: " + message.getKey(), e);
        } finally {
            try {
                // Cleanup temporary files
                if (originalFile != null) {
                    Files.deleteIfExists(originalFile);
                }
                if (thumbnailFile != null) {
                    Files.deleteIfExists(thumbnailFile);
                }
                if (tempDir != null) {
                    Files.deleteIfExists(tempDir);
                }

                if (processingSuccess) {
                    // Acknowledge the message if processing was successful
                    channel.basicAck(deliveryTag, false);
                    log.debug("Message acknowledged for: {}", message.getKey());
                } else {
                    // Reject the message with requeue=false to trigger dead letter exchange
                    // This will route the message to the retry queue with delay
                    channel.basicNack(deliveryTag, false, false);
                    log.debug("Message rejected and sent to dead letter exchange for delayed retry: {}", message.getKey());
                }
            } catch (IOException e) {
                log.error("Error handling RabbitMQ acknowledgment for: {}", message.getKey(), e);
            }
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
