package com.dropit.notification.messaging.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Configuration
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true")
public class NotificationSqsListenerConfig {

    @Bean
    SqsMessageListenerContainerFactory<Object> notificationSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient
    ) {
        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options.acknowledgementMode(AcknowledgementMode.ON_SUCCESS))
                .build();
    }
}
