package com.microsoft.migration.assets.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String QUEUE_NAME = "image-processing";

    // We need to use the same queue definition as the worker to avoid conflicts
    @Bean
    public Queue imageProcessingQueue() {
        // Using QueueBuilder with same arguments as worker for compatibility
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", "image-processing.retry")
                .withArgument("x-dead-letter-routing-key", "retry")
                .build();
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
