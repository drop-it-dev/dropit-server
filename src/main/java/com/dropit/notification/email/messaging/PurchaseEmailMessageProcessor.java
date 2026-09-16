package com.dropit.notification.email.messaging;

import com.dropit.notification.email.delivery.EmailDeliveryClaim;
import com.dropit.notification.email.delivery.EmailDeliveryClaimStatus;
import com.dropit.notification.email.delivery.PurchaseEmailDeliveryService;
import com.dropit.notification.email.sender.PurchaseEmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PurchaseEmailMessageProcessor {

    private static final int MARK_SENT_MAX_ATTEMPTS = 3;

    private final PurchaseEmailDeliveryService deliveryService;
    private final PurchaseEmailSender emailSender;
    private final EmailSqsProperties properties;

    public void process(PurchaseCompletedMessage message) {
        EmailDeliveryClaim claim = deliveryService.claim(
                message,
                Instant.now(),
                Duration.ofSeconds(properties.consumerVisibilityTimeoutSeconds())
        );

        if (claim.status() == EmailDeliveryClaimStatus.ALREADY_SENT) {
            return;
        }
        if (claim.status() == EmailDeliveryClaimStatus.IN_PROGRESS) {
            throw new IllegalStateException("다른 Consumer가 이메일 이벤트를 처리 중입니다.");
        }

        String providerMessageId;
        try {
            providerMessageId = emailSender.send(message);
        } catch (RuntimeException exception) {
            releaseForRetry(message, claim, exception);
            throw exception;
        }

        markSent(message, claim, providerMessageId);
    }

    private void markSent(
            PurchaseCompletedMessage message,
            EmailDeliveryClaim claim,
            String providerMessageId
    ) {
        for (int attempt = 1; attempt <= MARK_SENT_MAX_ATTEMPTS; attempt++) {
            try {
                deliveryService.markSent(
                        message.eventId(),
                        claim.claimToken(),
                        providerMessageId,
                        Instant.now()
                );
                return;
            } catch (RuntimeException exception) {
                if (attempt == MARK_SENT_MAX_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }

    private void releaseForRetry(
            PurchaseCompletedMessage message,
            EmailDeliveryClaim claim,
            RuntimeException exception
    ) {
        try {
            deliveryService.releaseForRetry(
                    message.eventId(),
                    claim.claimToken(),
                    exception.getMessage()
            );
        } catch (RuntimeException retryException) {
            exception.addSuppressed(retryException);
        }
    }
}
