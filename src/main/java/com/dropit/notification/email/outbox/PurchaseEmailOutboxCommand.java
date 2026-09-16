package com.dropit.notification.email.outbox;

import java.util.UUID;

public record PurchaseEmailOutboxCommand(
        UUID eventId,
        UUID claimToken,
        String payload
) {
}
