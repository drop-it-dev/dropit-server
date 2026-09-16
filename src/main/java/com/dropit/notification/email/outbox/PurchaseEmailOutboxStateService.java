package com.dropit.notification.email.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseEmailOutboxStateService {

    private final PurchaseEmailOutboxRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PurchaseEmailOutboxCommand> claim(
            UUID eventId,
            Instant now,
            Duration claimTimeout
    ) {
        Optional<PurchaseEmailOutbox> optionalOutbox =
                outboxRepository.findByEventIdForUpdate(eventId);
        if (optionalOutbox.isEmpty()) {
            return Optional.empty();
        }

        PurchaseEmailOutbox outbox = optionalOutbox.get();
        UUID claimToken = UUID.randomUUID();
        if (!outbox.claim(claimToken, now.plus(claimTimeout), now)) {
            return Optional.empty();
        }

        return Optional.of(new PurchaseEmailOutboxCommand(
                outbox.getEventId(),
                claimToken,
                outbox.getPayload()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(
            UUID eventId,
            UUID claimToken,
            Instant publishedAt
    ) {
        PurchaseEmailOutbox outbox = findForUpdate(eventId);
        if (!outbox.markPublished(claimToken, publishedAt)) {
            throw new IllegalStateException("이메일 Outbox 발행 소유권을 확인할 수 없습니다.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleRetry(
            UUID eventId,
            UUID claimToken,
            Instant nextAttemptAt,
            String errorMessage
    ) {
        PurchaseEmailOutbox outbox = findForUpdate(eventId);
        if (!outbox.scheduleRetry(claimToken, nextAttemptAt, errorMessage)) {
            throw new IllegalStateException("이메일 Outbox 재시도 소유권을 확인할 수 없습니다.");
        }
    }

    private PurchaseEmailOutbox findForUpdate(UUID eventId) {
        return outboxRepository.findByEventIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "이메일 Outbox를 찾을 수 없습니다. eventId=" + eventId
                ));
    }
}
