package com.dropit.notification.email.sender;

import com.dropit.notification.email.messaging.PurchaseCompletedMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.text.NumberFormat;
import java.util.Locale;

@Component
public class PurchaseEmailTemplate {

    public PurchaseEmailContent create(PurchaseCompletedMessage message) {
        String username = HtmlUtils.htmlEscape(message.username());
        String productName = HtmlUtils.htmlEscape(message.productName());
        String formattedPrice = NumberFormat.getNumberInstance(Locale.KOREA)
                .format(message.totalPrice());

        String subject = "[DROPIT] 구매가 완료되었습니다.";
        String textBody = String.format("""
                %s님, 구매가 완료되었습니다.

                주문 번호: %d
                상품: %s
                수량: %d개
                결제 금액: %s원
                """,
                message.username(),
                message.orderId(),
                message.productName(),
                message.quantity(),
                formattedPrice
        );
        String htmlBody = String.format("""
                <!doctype html>
                <html lang="ko">
                <body>
                  <h1>구매가 완료되었습니다.</h1>
                  <p>%s님, DROPIT을 이용해 주셔서 감사합니다.</p>
                  <ul>
                    <li>주문 번호: %d</li>
                    <li>상품: %s</li>
                    <li>수량: %d개</li>
                    <li>결제 금액: %s원</li>
                  </ul>
                </body>
                </html>
                """,
                username,
                message.orderId(),
                productName,
                message.quantity(),
                formattedPrice
        );

        return new PurchaseEmailContent(subject, textBody, htmlBody);
    }
}
