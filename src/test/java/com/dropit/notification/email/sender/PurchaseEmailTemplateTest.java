package com.dropit.notification.email.sender;

import com.dropit.notification.email.messaging.PurchaseCompletedMessage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseEmailTemplateTest {

    private final PurchaseEmailTemplate template = new PurchaseEmailTemplate();

    @Test
    void 구매_완료_정보로_텍스트와_HTML_본문을_생성한다() {
        PurchaseEmailContent content = template.create(message("한정판 후드티"));

        assertThat(content.subject()).isEqualTo("[DROPIT] 구매가 완료되었습니다.");
        assertThat(content.textBody())
                .contains("로컬구매자님")
                .contains("주문 번호: 10")
                .contains("상품: 한정판 후드티")
                .contains("결제 금액: 49,000원");
        assertThat(content.htmlBody())
                .contains("<h1>구매가 완료되었습니다.</h1>")
                .contains("한정판 후드티");
    }

    @Test
    void 상품명의_HTML_문자를_이스케이프한다() {
        PurchaseEmailContent content = template.create(message("<script>alert('x')</script>"));

        assertThat(content.htmlBody())
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    private PurchaseCompletedMessage message(String productName) {
        return new PurchaseCompletedMessage(
                PurchaseCompletedMessage.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                10L,
                1L,
                "buyer@example.com",
                "로컬구매자",
                productName,
                1,
                new BigDecimal("49000"),
                Instant.parse("2026-09-16T02:00:00Z")
        );
    }
}
