package com.dropit.notification.messaging.consumer;

import com.dropit.notification.messaging.dto.NotificationEvent;
import com.dropit.notification.service.NotificationService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true")
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    @SqsListener(
            value = "${aws.sqs.notification-queue-url}",
            factory = "notificationSqsListenerContainerFactory"
    )
    public void receive(NotificationEvent event) {
        notificationService.create(
                event.userId(),
                event.title(),
                event.message()
        );
    }
}
