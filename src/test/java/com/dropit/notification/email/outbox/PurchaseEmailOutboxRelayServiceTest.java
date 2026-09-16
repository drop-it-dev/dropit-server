package com.dropit.notification.email.outbox;

import com.dropit.notification.email.messaging.EmailSqsProperties;
import com.dropit.notification.email.messaging.PurchaseEmailMessagePublisher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseEmailOutboxRelayServiceTest {

    private final PurchaseEmailOutboxStateService stateService =
            mock(PurchaseEmailOutboxStateService.class);
    private final PurchaseEmailMessagePublisher messagePublisher =
            mock(PurchaseEmailMessagePublisher.class);
    private final EmailSqsProperties properties = new EmailSqsProperties(
            "https://sqs.ap-northeast-2.amazonaws.com/123/purchase-email",
            true,
            3000,
            50,
            30000,
            5000
    );
    private final PurchaseEmailOutboxRelayService relayService =
            new PurchaseEmailOutboxRelayService(
                    stateService,
                    messagePublisher,
                    properties
            );

    @Test
    void SQS_발행에_성공하면_Outbox를_발행완료로_변경한다() {
        UUID eventId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();
        PurchaseEmailOutboxCommand command = new PurchaseEmailOutboxCommand(
                eventId,
                claimToken,
                "{\"orderId\":1}"
        );
        when(stateService.claim(
                eq(eventId),
                any(Instant.class),
                eq(Duration.ofMillis(30000))
        )).thenReturn(Optional.of(command));
        when(messagePublisher.publish(command.payload())).thenReturn("message-1");

        relayService.publish(eventId);

        verify(messagePublisher).publish(command.payload());
        verify(stateService).markPublished(
                eq(eventId),
                eq(claimToken),
                any(Instant.class)
        );
    }

    @Test
    void SQS_발행에_실패하면_Outbox_재시도를_예약한다() {
        UUID eventId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();
        PurchaseEmailOutboxCommand command = new PurchaseEmailOutboxCommand(
                eventId,
                claimToken,
                "{\"orderId\":1}"
        );
        when(stateService.claim(
                eq(eventId),
                any(Instant.class),
                eq(Duration.ofMillis(30000))
        )).thenReturn(Optional.of(command));
        when(messagePublisher.publish(command.payload()))
                .thenThrow(new IllegalStateException("SQS 연결 실패"));

        assertThatThrownBy(() -> relayService.publish(eventId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SQS 연결 실패");

        verify(stateService).scheduleRetry(
                eq(eventId),
                eq(claimToken),
                any(Instant.class),
                eq("SQS 연결 실패")
        );
    }
}
