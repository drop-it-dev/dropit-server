package com.dropit.notification.email.sender;

import com.dropit.notification.email.messaging.PurchaseCompletedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class AwsSesPurchaseEmailSender implements PurchaseEmailSender {

    private final SesClient sesClient;
    private final EmailSesProperties properties;
    private final PurchaseEmailTemplate template;

    @Override
    public String send(PurchaseCompletedMessage message) {
        validate(message);
        PurchaseEmailContent content = template.create(message);

        SendEmailResponse response = sesClient.sendEmail(SendEmailRequest.builder()
                .source(properties.fromAddress())
                .destination(Destination.builder()
                        .toAddresses(message.recipientEmail())
                        .build())
                .message(Message.builder()
                        .subject(content(content.subject()))
                        .body(Body.builder()
                                .text(content(content.textBody()))
                                .html(content(content.htmlBody()))
                                .build())
                        .build())
                .build());

        if (response.messageId() == null || response.messageId().isBlank()) {
            throw new IllegalStateException("AWS SES가 이메일 메시지 ID를 반환하지 않았습니다.");
        }
        return response.messageId();
    }

    private Content content(String value) {
        return Content.builder()
                .charset(StandardCharsets.UTF_8.name())
                .data(value)
                .build();
    }

    private void validate(PurchaseCompletedMessage message) {
        if (properties.fromAddress() == null || properties.fromAddress().isBlank()) {
            throw new IllegalStateException("구매 성공 이메일 발신 주소가 설정되지 않았습니다.");
        }
        if (message.recipientEmail() == null || message.recipientEmail().isBlank()) {
            throw new IllegalArgumentException("구매 성공 이메일 수신 주소가 비어 있습니다.");
        }
    }
}
