package com.dropit.notification.email.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseEmailOutboxTest {

    @Test
    void 발행_소유권을_획득한_작업만_발행완료로_변경할_수_있다() {
        Instant now = Instant.parse("2026-09-16T04:00:00Z");
        PurchaseEmailOutbox outbox = new PurchaseEmailOutbox(
                UUID.randomUUID(),
                1L,
                "{}",
                now
        );
        UUID claimToken = UUID.randomUUID();

        assertThat(outbox.claim(claimToken, now.plusSeconds(30), now)).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.PROCESSING);
        assertThat(outbox.markPublished(UUID.randomUUID(), now)).isFalse();
        assertThat(outbox.markPublished(claimToken, now)).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.PUBLISHED);
        assertThat(outbox.getPublishedAt()).isEqualTo(now);
        assertThat(outbox.getClaimToken()).isNull();
    }

    @Test
    void 발행에_실패하면_PENDING으로_돌리고_재시도_정보를_기록한다() {
        Instant now = Instant.parse("2026-09-16T04:00:00Z");
        PurchaseEmailOutbox outbox = new PurchaseEmailOutbox(
                UUID.randomUUID(),
                1L,
                "{}",
                now
        );
        UUID claimToken = UUID.randomUUID();
        Instant nextAttemptAt = now.plusSeconds(5);
        outbox.claim(claimToken, now.plusSeconds(30), now);

        assertThat(outbox.scheduleRetry(
                claimToken,
                nextAttemptAt,
                "SQS 연결 실패"
        )).isTrue();

        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(nextAttemptAt);
        assertThat(outbox.getLastError()).isEqualTo("SQS 연결 실패");
        assertThat(outbox.getClaimToken()).isNull();
    }
}
