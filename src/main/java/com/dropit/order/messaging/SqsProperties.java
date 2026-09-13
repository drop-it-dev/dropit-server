package com.dropit.order.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.order.sqs")
public record SqsProperties(
        String orderQueueUrl,
        boolean consumerEnabled,
        int consumerConcurrency,
        int consumerMaxNumberOfMessages,
        int consumerWaitTimeSeconds,
        int consumerVisibilityTimeoutSeconds,
        long consumerInitialDelay,
        long consumerPollDelay
) {
}
