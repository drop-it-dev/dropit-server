package com.dropit.notification.email.delivery;

import java.util.UUID;

public record EmailDeliveryClaim(
        EmailDeliveryClaimStatus status,
        UUID claimToken
) {

    public static EmailDeliveryClaim claimed(UUID claimToken) {
        return new EmailDeliveryClaim(EmailDeliveryClaimStatus.CLAIMED, claimToken);
    }

    public static EmailDeliveryClaim alreadySent() {
        return new EmailDeliveryClaim(EmailDeliveryClaimStatus.ALREADY_SENT, null);
    }

    public static EmailDeliveryClaim inProgress() {
        return new EmailDeliveryClaim(EmailDeliveryClaimStatus.IN_PROGRESS, null);
    }
}
