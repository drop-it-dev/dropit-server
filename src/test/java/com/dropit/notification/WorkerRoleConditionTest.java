package com.dropit.notification;

import com.dropit.notification.email.messaging.PurchaseEmailSqsListener;
import com.dropit.notification.email.messaging.PurchaseEmailSqsListenerConfig;
import com.dropit.notification.email.outbox.PurchaseEmailOutboxRelayScheduler;
import com.dropit.notification.messaging.config.NotificationSqsListenerConfig;
import com.dropit.notification.messaging.consumer.NotificationConsumer;
import com.dropit.notification.scheduler.WishlistNotificationScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerRoleConditionTest {

    @Test
    @DisplayName("API 인스턴스에서는 알림 Consumer와 Scheduler를 생성하지 않는다")
    void requireWorkerRoleForBackgroundNotificationJobs() {
        assertWorkerOnly(WishlistNotificationScheduler.class);
        assertWorkerOnly(NotificationSqsListenerConfig.class);
        assertWorkerOnly(NotificationConsumer.class);
        assertWorkerOnly(PurchaseEmailOutboxRelayScheduler.class);
        assertWorkerOnly(PurchaseEmailSqsListenerConfig.class);
        assertWorkerOnly(PurchaseEmailSqsListener.class);
    }

    private void assertWorkerOnly(Class<?> type) {
        ConditionalOnProperty condition = type.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).as(type.getSimpleName()).isNotNull();
        assertThat(Arrays.asList(condition.name())).contains("app.worker.enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
    }
}
