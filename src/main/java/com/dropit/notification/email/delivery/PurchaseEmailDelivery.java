package com.dropit.notification.email.delivery;

import com.dropit.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "purchase_email_delivery",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_purchase_email_delivery_order_id",
                columnNames = "order_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseEmailDelivery extends BaseEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailDeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "claim_expires_at")
    private Instant claimExpiresAt;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public PurchaseEmailDelivery(UUID eventId, Long orderId) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.status = EmailDeliveryStatus.PENDING;
        this.attemptCount = 0;
    }

    public boolean claim(UUID claimToken, Instant claimExpiresAt, Instant now) {
        if (status == EmailDeliveryStatus.SENT) {
            return false;
        }
        if (status == EmailDeliveryStatus.PROCESSING
                && this.claimExpiresAt != null
                && this.claimExpiresAt.isAfter(now)) {
            return false;
        }

        this.status = EmailDeliveryStatus.PROCESSING;
        this.claimToken = claimToken;
        this.claimExpiresAt = claimExpiresAt;
        return true;
    }

    public boolean markSent(UUID claimToken, String providerMessageId, Instant sentAt) {
        if (!ownsClaim(claimToken)) {
            return false;
        }

        this.status = EmailDeliveryStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = sentAt;
        this.lastError = null;
        clearClaim();
        return true;
    }

    public boolean releaseForRetry(UUID claimToken, String errorMessage) {
        if (!ownsClaim(claimToken)) {
            return false;
        }

        this.status = EmailDeliveryStatus.PENDING;
        this.attemptCount++;
        this.lastError = truncate(errorMessage);
        clearClaim();
        return true;
    }

    public boolean isSent() {
        return status == EmailDeliveryStatus.SENT;
    }

    private boolean ownsClaim(UUID claimToken) {
        return status == EmailDeliveryStatus.PROCESSING
                && this.claimToken != null
                && this.claimToken.equals(claimToken);
    }

    private void clearClaim() {
        this.claimToken = null;
        this.claimExpiresAt = null;
    }

    private String truncate(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= 1000) {
            return errorMessage;
        }
        return errorMessage.substring(0, 1000);
    }
}
