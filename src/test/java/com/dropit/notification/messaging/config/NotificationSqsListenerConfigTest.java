package com.dropit.notification.messaging.config;

import com.dropit.notification.messaging.consumer.NotificationConsumer;
import com.dropit.notification.messaging.dto.NotificationEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class NotificationSqsListenerConfigTest {

    private final NotificationSqsListenerConfig config = new NotificationSqsListenerConfig();

    @Test
    @DisplayName("알림 Consumer는 전용 팩토리로 성공 처리된 메시지를 ACK한다")
    void configureNotificationListenerFactory() throws NoSuchMethodException {
        var factory = config.notificationSqsListenerContainerFactory(mock(SqsAsyncClient.class));
        var container = factory.createContainer("notification-queue");
        var listener = NotificationConsumer.class
                .getDeclaredMethod("receive", NotificationEvent.class)
                .getAnnotation(SqsListener.class);

        assertEquals("notificationSqsListenerContainerFactory", listener.factory());
        assertEquals(AcknowledgementMode.ON_SUCCESS,
                container.getContainerOptions().getAcknowledgementMode());
    }
}
