package com.dropit.notification.email.delivery;

import com.dropit.notification.email.messaging.PurchaseCompletedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseEmailDeliveryService {

    private final PurchaseEmailDeliveryRepository deliveryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailDeliveryClaim claim(
            PurchaseCompletedMessage message,
            Instant now,
            Duration claimTimeout
    ) {
        PurchaseEmailDelivery delivery = deliveryRepository
                .findByEventIdForUpdate(message.eventId())
                .orElseGet(() -> deliveryRepository.saveAndFlush(
                        new PurchaseEmailDelivery(message.eventId(), message.orderId())
                ));

        if (delivery.isSent()) {
            return EmailDeliveryClaim.alreadySent();
        }

        UUID claimToken = UUID.randomUUID();
        if (!delivery.claim(claimToken, now.plus(claimTimeout), now)) {
            return EmailDeliveryClaim.inProgress();
        }
        return EmailDeliveryClaim.claimed(claimToken);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(
            UUID eventId,
            UUID claimToken,
            String providerMessageId,
            Instant sentAt
    ) {
        PurchaseEmailDelivery delivery = findForUpdate(eventId);
        if (!delivery.markSent(claimToken, providerMessageId, sentAt)) {
            throw new IllegalStateException("이메일 발송 완료 처리 권한을 확인할 수 없습니다.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseForRetry(
            UUID eventId,
            UUID claimToken,
            String errorMessage
    ) {
        PurchaseEmailDelivery delivery = findForUpdate(eventId);
        if (!delivery.releaseForRetry(claimToken, errorMessage)) {
            throw new IllegalStateException("이메일 발송 재시도 처리 권한을 확인할 수 없습니다.");
        }
    }

    private PurchaseEmailDelivery findForUpdate(UUID eventId) {
        return deliveryRepository.findByEventIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "이메일 발송 기록을 찾을 수 없습니다. eventId=" + eventId
                ));
    }
}
