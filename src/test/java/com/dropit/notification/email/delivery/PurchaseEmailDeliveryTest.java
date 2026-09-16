package com.dropit.notification.email.delivery;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseEmailDeliveryTest {

    @Test
    void Claim을_가진_처리만_발송완료로_변경할_수_있다() {
        PurchaseEmailDelivery delivery = new PurchaseEmailDelivery(UUID.randomUUID(), 10L);
        UUID claimToken = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-16T02:00:00Z");

        assertThat(delivery.claim(claimToken, now.plusSeconds(60), now)).isTrue();
        assertThat(delivery.markSent(UUID.randomUUID(), "wrong", now)).isFalse();
        assertThat(delivery.markSent(claimToken, "ses-message-id", now)).isTrue();
        assertThat(delivery.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(delivery.getProviderMessageId()).isEqualTo("ses-message-id");
        assertThat(delivery.getClaimToken()).isNull();
    }

    @Test
    void 이미_발송된_이벤트는_다시_Claim할_수_없다() {
        PurchaseEmailDelivery delivery = new PurchaseEmailDelivery(UUID.randomUUID(), 10L);
        UUID claimToken = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-16T02:00:00Z");

        delivery.claim(claimToken, now.plusSeconds(60), now);
        delivery.markSent(claimToken, "ses-message-id", now);

        assertThat(delivery.claim(UUID.randomUUID(), now.plusSeconds(120), now.plusSeconds(61)))
                .isFalse();
    }

    @Test
    void 발송_실패는_재시도_가능한_상태로_되돌린다() {
        PurchaseEmailDelivery delivery = new PurchaseEmailDelivery(UUID.randomUUID(), 10L);
        UUID claimToken = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-16T02:00:00Z");

        delivery.claim(claimToken, now.plusSeconds(60), now);

        assertThat(delivery.releaseForRetry(claimToken, "SES 연결 실패")).isTrue();
        assertThat(delivery.getStatus()).isEqualTo(EmailDeliveryStatus.PENDING);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getLastError()).isEqualTo("SES 연결 실패");
    }
}
