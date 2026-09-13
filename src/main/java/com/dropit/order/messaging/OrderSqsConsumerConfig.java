package com.dropit.order.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class OrderSqsConsumerConfig {

    @Bean(destroyMethod = "shutdown")
    ThreadPoolExecutor sqsOrderConsumerExecutor(SqsProperties properties) {
        if (properties.consumerConcurrency() <= 0) {
            throw new IllegalArgumentException("app.order.sqs.consumer-concurrency must be positive");
        }
        validateActiveConsumer(properties);

        return new ThreadPoolExecutor(
                properties.consumerConcurrency(),
                properties.consumerConcurrency(),
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private void validateActiveConsumer(SqsProperties properties) {
        if (!properties.consumerEnabled()) {
            return;
        }
        if (properties.orderQueueUrl() == null || properties.orderQueueUrl().isBlank()) {
            throw new IllegalArgumentException("app.order.sqs.order-queue-url must be configured");
        }

        requireRange(properties.consumerMaxNumberOfMessages(), 1, 10,
                "app.order.sqs.consumer-max-number-of-messages");
        requireRange(properties.consumerWaitTimeSeconds(), 0, 20,
                "app.order.sqs.consumer-wait-time-seconds");
        requireRange(properties.consumerVisibilityTimeoutSeconds(), 0, 43_200,
                "app.order.sqs.consumer-visibility-timeout-seconds");
    }

    private void requireRange(int value, int min, int max, String propertyName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    propertyName + " must be between " + min + " and " + max
            );
        }
    }
}
