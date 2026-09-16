package com.dropit.notification.email.messaging;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.QueueNotFoundStrategy;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(
        prefix = "app.notification.email.sqs",
        name = "consumer-enabled",
        havingValue = "true"
)
public class PurchaseEmailSqsListenerConfig {

    @Bean
    SqsMessageListenerContainerFactory<Object> purchaseEmailSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient,
            EmailSqsProperties properties
    ) {
        validate(properties);

        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options
                        .autoStartup(true)
                        .acknowledgementMode(AcknowledgementMode.MANUAL)
                        .acknowledgementInterval(Duration.ZERO)
                        .acknowledgementThreshold(0)
                        .maxConcurrentMessages(properties.consumerConcurrency())
                        .maxMessagesPerPoll(properties.consumerMaxNumberOfMessages())
                        .pollTimeout(Duration.ofSeconds(properties.consumerWaitTimeSeconds()))
                        .messageVisibility(Duration.ofSeconds(
                                properties.consumerVisibilityTimeoutSeconds()
                        ))
                        .queueNotFoundStrategy(QueueNotFoundStrategy.FAIL))
                .build();
    }

    private void validate(EmailSqsProperties properties) {
        if (properties.queueUrl() == null || properties.queueUrl().isBlank()) {
            throw new IllegalArgumentException(
                    "app.notification.email.sqs.queue-url must be configured"
            );
        }
        requireRange(
                properties.consumerConcurrency(),
                1,
                Integer.MAX_VALUE,
                "app.notification.email.sqs.consumer-concurrency"
        );
        requireRange(
                properties.consumerMaxNumberOfMessages(),
                1,
                10,
                "app.notification.email.sqs.consumer-max-number-of-messages"
        );
        requireRange(
                properties.consumerWaitTimeSeconds(),
                0,
                20,
                "app.notification.email.sqs.consumer-wait-time-seconds"
        );
        requireRange(
                properties.consumerVisibilityTimeoutSeconds(),
                1,
                43_200,
                "app.notification.email.sqs.consumer-visibility-timeout-seconds"
        );
    }

    private void requireRange(int value, int min, int max, String propertyName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    propertyName + " must be between " + min + " and " + max
            );
        }
    }
}
