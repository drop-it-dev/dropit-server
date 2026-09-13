package com.dropit.notification.messaging.producer;

import com.dropit.notification.messaging.dto.NotificationEvent;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationProducer {

    private final SqsTemplate sqsTemplate;

    @Value("${aws.sqs.notification-queue-name}")
    private String queueName;

    public void send(NotificationEvent event) {
        sqsTemplate.send(queueName, event);
    }
}
