package com.dropit.notification.email.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.email.sqs")
public record EmailSqsProperties(
        String queueUrl,
        boolean publisherEnabled,
        long publisherTimeoutMillis,
        int relayBatchSize,
        long claimTimeoutMillis,
        long retryDelayMillis,
        boolean consumerEnabled,
        int consumerConcurrency,
        int consumerMaxNumberOfMessages,
        int consumerWaitTimeSeconds,
        int consumerVisibilityTimeoutSeconds
) {
}
