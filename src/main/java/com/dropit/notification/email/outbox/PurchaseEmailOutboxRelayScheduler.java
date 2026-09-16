package com.dropit.notification.email.outbox;

import com.dropit.notification.email.messaging.EmailSqsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.notification.email.sqs",
        name = "publisher-enabled",
        havingValue = "true"
)
public class PurchaseEmailOutboxRelayScheduler {

    private final PurchaseEmailOutboxRepository outboxRepository;
    private final PurchaseEmailOutboxRelayService relayService;
    private final EmailSqsProperties properties;

    @Scheduled(
            fixedDelayString = "${app.notification.email.sqs.relay-delay-millis:1000}"
    )
    public void publishPending() {
        for (UUID eventId : outboxRepository.findPublishableEventIds(
                Instant.now(),
                PageRequest.of(0, properties.relayBatchSize())
        )) {
            try {
                relayService.publish(eventId);
            } catch (RuntimeException exception) {
                log.warn("구매 성공 이메일 Outbox 발행에 실패했습니다. eventId={}", eventId, exception);
            }
        }
    }
}
