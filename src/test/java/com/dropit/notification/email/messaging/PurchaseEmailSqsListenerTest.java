package com.dropit.notification.email.messaging;

import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PurchaseEmailSqsListenerTest {

    private final PurchaseEmailMessageProcessor processor =
            mock(PurchaseEmailMessageProcessor.class);
    private final PurchaseEmailSqsListener listener =
            new PurchaseEmailSqsListener(processor);
    private final Acknowledgement acknowledgement = mock(Acknowledgement.class);

    @Test
    void 이메일_처리에_성공한_메시지만_ACK한다() {
        PurchaseCompletedMessage message = message();

        listener.listen(message, acknowledgement);

        verify(processor).process(message);
        verify(acknowledgement).acknowledge();
    }

    @Test
    void 이메일_처리에_실패한_메시지는_ACK하지_않는다() {
        PurchaseCompletedMessage message = message();
        doThrow(new IllegalStateException("SES 연결 실패"))
                .when(processor).process(message);

        assertThatThrownBy(() -> listener.listen(message, acknowledgement))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SES 연결 실패");
        verify(acknowledgement, never()).acknowledge();
    }

    private PurchaseCompletedMessage message() {
        return new PurchaseCompletedMessage(
                PurchaseCompletedMessage.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                10L,
                1L,
                "buyer@example.com",
                "로컬구매자",
                "한정판 후드티",
                1,
                new BigDecimal("49000"),
                Instant.parse("2026-09-16T02:00:00Z")
        );
    }
}
