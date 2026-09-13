package com.dropit.order.messaging;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.QueueNotFoundStrategy;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;

@Configuration
public class OrderSqsListenerConfig {

    @Bean
    SqsMessageListenerContainerFactory<Object> orderSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient,
            SqsProperties properties
    ) {
        validate(properties);

        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options
                        .autoStartup(properties.consumerEnabled())
                        .acknowledgementMode(AcknowledgementMode.MANUAL)
                        .acknowledgementInterval(Duration.ZERO)
                        .acknowledgementThreshold(0)
                        .maxConcurrentMessages(properties.consumerConcurrency())
                        .maxMessagesPerPoll(properties.consumerMaxNumberOfMessages())
                        .pollTimeout(Duration.ofSeconds(properties.consumerWaitTimeSeconds()))
                        .messageVisibility(Duration.ofSeconds(properties.consumerVisibilityTimeoutSeconds()))
                        .queueNotFoundStrategy(QueueNotFoundStrategy.FAIL))
                .build();
    }

    private void validate(SqsProperties properties) {
        requireRange(properties.consumerConcurrency(), 1, Integer.MAX_VALUE,
                "app.order.sqs.consumer-concurrency");
        requireRange(properties.consumerMaxNumberOfMessages(), 1, 10,
                "app.order.sqs.consumer-max-number-of-messages");
        requireRange(properties.consumerWaitTimeSeconds(), 0, 20,
                "app.order.sqs.consumer-wait-time-seconds");
        requireRange(properties.consumerVisibilityTimeoutSeconds(), 0, 43_200,
                "app.order.sqs.consumer-visibility-timeout-seconds");
        if (properties.publisherTimeoutMillis() <= 0) {
            throw new IllegalArgumentException("app.order.sqs.publisher-timeout-millis must be positive");
        }
        if (properties.consumerEnabled()
                && (properties.orderQueueUrl() == null || properties.orderQueueUrl().isBlank())) {
            throw new IllegalArgumentException("app.order.sqs.order-queue-url must be configured");
        }
    }

    private void requireRange(int value, int min, int max, String propertyName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(propertyName + " must be between " + min + " and " + max);
        }
    }
}
