package com.dropit.notification.email.outbox;

import com.dropit.notification.email.messaging.EmailSqsProperties;
import com.dropit.notification.email.messaging.PurchaseEmailMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseEmailOutboxRelayService {

    private final PurchaseEmailOutboxStateService stateService;
    private final PurchaseEmailMessagePublisher messagePublisher;
    private final EmailSqsProperties properties;

    public void publish(UUID eventId) {
        Optional<PurchaseEmailOutboxCommand> optionalCommand = stateService.claim(
                eventId,
                Instant.now(),
                Duration.ofMillis(properties.claimTimeoutMillis())
        );
        if (optionalCommand.isEmpty()) {
            return;
        }

        PurchaseEmailOutboxCommand command = optionalCommand.get();
        try {
            messagePublisher.publish(command.payload());
            stateService.markPublished(
                    command.eventId(),
                    command.claimToken(),
                    Instant.now()
            );
        } catch (RuntimeException exception) {
            scheduleRetry(command, exception);
            throw exception;
        }
    }

    private void scheduleRetry(
            PurchaseEmailOutboxCommand command,
            RuntimeException exception
    ) {
        try {
            stateService.scheduleRetry(
                    command.eventId(),
                    command.claimToken(),
                    Instant.now().plusMillis(properties.retryDelayMillis()),
                    exception.getMessage()
            );
        } catch (RuntimeException retryException) {
            exception.addSuppressed(retryException);
        }
    }
}
