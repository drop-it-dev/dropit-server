package com.dropit.notification.email.messaging;

import com.dropit.notification.email.delivery.EmailDeliveryClaim;
import com.dropit.notification.email.delivery.PurchaseEmailDeliveryService;
import com.dropit.notification.email.sender.PurchaseEmailSender;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PurchaseEmailMessageProcessorTest {

    private final PurchaseEmailDeliveryService deliveryService =
            mock(PurchaseEmailDeliveryService.class);
    private final PurchaseEmailSender emailSender = mock(PurchaseEmailSender.class);
    private final EmailSqsProperties properties = new EmailSqsProperties(
            "queue-url",
            true,
            3000,
            50,
            30000,
            5000,
            true,
            2,
            2,
            10,
            60
    );
    private final PurchaseEmailMessageProcessor processor =
            new PurchaseEmailMessageProcessor(deliveryService, emailSender, properties);

    @Test
    void Claim에_성공하면_SES_발송후_SENT로_변경한다() {
        PurchaseCompletedMessage message = message();
        UUID claimToken = UUID.randomUUID();
        when(deliveryService.claim(eq(message), any(), any()))
                .thenReturn(EmailDeliveryClaim.claimed(claimToken));
        when(emailSender.send(message)).thenReturn("ses-message-id");

        processor.process(message);

        verify(emailSender).send(message);
        verify(deliveryService).markSent(
                eq(message.eventId()),
                eq(claimToken),
                eq("ses-message-id"),
                any()
        );
    }

    @Test
    void 이미_발송된_이벤트는_SES를_다시_호출하지_않는다() {
        PurchaseCompletedMessage message = message();
        when(deliveryService.claim(eq(message), any(), any()))
                .thenReturn(EmailDeliveryClaim.alreadySent());

        processor.process(message);

        verifyNoInteractions(emailSender);
        verify(deliveryService, never()).markSent(any(), any(), any(), any());
    }

    @Test
    void 다른_Consumer가_처리중이면_발송하지_않고_예외를_발생시킨다() {
        PurchaseCompletedMessage message = message();
        when(deliveryService.claim(eq(message), any(), any()))
                .thenReturn(EmailDeliveryClaim.inProgress());

        assertThatThrownBy(() -> processor.process(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("다른 Consumer가 이메일 이벤트를 처리 중입니다.");
        verifyNoInteractions(emailSender);
    }

    @Test
    void SES_발송에_실패하면_재시도_상태로_되돌린다() {
        PurchaseCompletedMessage message = message();
        UUID claimToken = UUID.randomUUID();
        when(deliveryService.claim(eq(message), any(), any()))
                .thenReturn(EmailDeliveryClaim.claimed(claimToken));
        when(emailSender.send(message)).thenThrow(new IllegalStateException("SES 연결 실패"));

        assertThatThrownBy(() -> processor.process(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SES 연결 실패");
        verify(deliveryService).releaseForRetry(
                message.eventId(),
                claimToken,
                "SES 연결 실패"
        );
    }

    @Test
    void 발송후_SENT_저장에_실패하면_같은_메시지_ID로_재시도한다() {
        PurchaseCompletedMessage message = message();
        UUID claimToken = UUID.randomUUID();
        when(deliveryService.claim(eq(message), any(), any()))
                .thenReturn(EmailDeliveryClaim.claimed(claimToken));
        when(emailSender.send(message)).thenReturn("ses-message-id");
        org.mockito.Mockito.doThrow(new IllegalStateException("DB 연결 실패"))
                .doNothing()
                .when(deliveryService)
                .markSent(eq(message.eventId()), eq(claimToken), eq("ses-message-id"), any());

        processor.process(message);

        verify(emailSender).send(message);
        verify(deliveryService, times(2)).markSent(
                eq(message.eventId()),
                eq(claimToken),
                eq("ses-message-id"),
                any()
        );
        verify(deliveryService, never()).releaseForRetry(any(), any(), any());
    }

    @Test
    void SENT_저장이_계속_실패해도_발송_Claim을_해제하지_않는다() {
        PurchaseCompletedMessage message = message();
        UUID claimToken = UUID.randomUUID();
        when(deliveryService.claim(eq(message), any(), any()))
                .thenReturn(EmailDeliveryClaim.claimed(claimToken));
        when(emailSender.send(message)).thenReturn("ses-message-id");
        org.mockito.Mockito.doThrow(new IllegalStateException("DB 연결 실패"))
                .when(deliveryService)
                .markSent(eq(message.eventId()), eq(claimToken), eq("ses-message-id"), any());

        assertThatThrownBy(() -> processor.process(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB 연결 실패");

        verify(emailSender).send(message);
        verify(deliveryService, times(3)).markSent(
                eq(message.eventId()),
                eq(claimToken),
                eq("ses-message-id"),
                any()
        );
        verify(deliveryService, never()).releaseForRetry(any(), any(), any());
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
