package com.dropit.notification.email.outbox;

import com.dropit.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "purchase_email_outbox",
        indexes = @Index(
                name = "idx_purchase_email_outbox_status_next_attempt",
                columnList = "status, next_attempt_at"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseEmailOutbox extends BaseEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "order_id", nullable = false, updatable = false, unique = true)
    private Long orderId;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "claim_expires_at")
    private Instant claimExpiresAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public PurchaseEmailOutbox(
            UUID eventId,
            Long orderId,
            String payload,
            Instant createdAt
    ) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.payload = payload;
        this.status = EmailOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = createdAt;
    }

    public boolean claim(UUID claimToken, Instant claimExpiresAt, Instant now) {
        if (!isPublishableAt(now)) {
            return false;
        }

        this.status = EmailOutboxStatus.PROCESSING;
        this.claimToken = claimToken;
        this.claimExpiresAt = claimExpiresAt;
        return true;
    }

    public boolean markPublished(UUID claimToken, Instant publishedAt) {
        if (!ownsClaim(claimToken)) {
            return false;
        }

        this.status = EmailOutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.lastError = null;
        clearClaim();
        return true;
    }

    public boolean scheduleRetry(
            UUID claimToken,
            Instant nextAttemptAt,
            String errorMessage
    ) {
        if (!ownsClaim(claimToken)) {
            return false;
        }

        this.status = EmailOutboxStatus.PENDING;
        this.attemptCount++;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = truncate(errorMessage);
        clearClaim();
        return true;
    }

    private boolean isPublishableAt(Instant now) {
        if (status == EmailOutboxStatus.PENDING) {
            return !nextAttemptAt.isAfter(now);
        }
        return status == EmailOutboxStatus.PROCESSING
                && claimExpiresAt != null
                && !claimExpiresAt.isAfter(now);
    }

    private boolean ownsClaim(UUID claimToken) {
        return status == EmailOutboxStatus.PROCESSING
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
