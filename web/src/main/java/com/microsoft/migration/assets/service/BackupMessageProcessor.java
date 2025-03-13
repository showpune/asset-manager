package com.microsoft.migration.assets.service;

import com.microsoft.migration.assets.model.ImageProcessingMessage;
import com.azure.spring.messaging.servicebus.implementation.core.annotation.ServiceBusListener;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.spring.messaging.servicebus.core.ServiceBusTemplate;
import com.azure.spring.messaging.servicebus.support.ServiceBusMessageHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.microsoft.migration.assets.config.RabbitConfig.QUEUE_NAME;

import java.io.IOException;

/**
 * A backup message processor that serves as a monitoring and logging service.
 * 
 * Only enabled when the "backup" profile is active.
 */
@Slf4j
@Component
@Profile("backup") 
public class BackupMessageProcessor {

    /**
     * Processes image messages from a backup queue for monitoring and resilience purposes.
     * Uses the same Azure Service Bus API pattern as the worker module.
     */
    @ServiceBusListener(destination = QUEUE_NAME)
    public void processBackupMessage(final ImageProcessingMessage message, 
                                    @Header(ServiceBusMessageHeaders.RECEIVED_MESSAGE_CONTEXT) ServiceBusReceivedMessageContext context) {
        try {
            log.info("[BACKUP] Monitoring message: {}", message.getKey());
            log.info("[BACKUP] Content type: {}, Storage: {}, Size: {}", 
                    message.getContentType(), message.getStorageType(), message.getSize());
            
            // Acknowledge the message
            context.complete();
            log.info("[BACKUP] Successfully processed message: {}", message.getKey());
        } catch (Exception e) {
            log.error("[BACKUP] Failed to process message: " + message.getKey(), e);
            
            try {
                // Reject the message and requeue it
                context.abandon();
                log.warn("[BACKUP] Message requeued: {}", message.getKey());
            } catch (Exception ackEx) {
                log.error("[BACKUP] Error handling Azure Service Bus acknowledgment: {}", message.getKey(), ackEx);
            }
        }
    }
}