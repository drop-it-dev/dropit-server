package com.dropit.notification.messaging.consumer;

import com.dropit.notification.messaging.dto.NotificationEvent;
import com.dropit.notification.service.NotificationService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    @SqsListener("${aws.sqs.notification-queue-name}")
    public void receive(NotificationEvent event) {
        notificationService.create(
                event.userId(),
                event.title(),
                event.message()
        );
    }
}
