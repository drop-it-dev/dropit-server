package com.dropit.notification.email.sender;

import com.dropit.notification.email.messaging.PurchaseCompletedMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AwsSesPurchaseEmailSenderTest {

    private final SesClient sesClient = mock(SesClient.class);
    private final PurchaseEmailTemplate template = new PurchaseEmailTemplate();

    @Test
    void SES로_구매_완료_이메일을_발송한다() {
        AwsSesPurchaseEmailSender sender = new AwsSesPurchaseEmailSender(
                sesClient,
                new EmailSesProperties("no-reply@dropit.example"),
                template
        );
        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("ses-message-id").build());

        String messageId = sender.send(message("buyer@example.com"));

        ArgumentCaptor<SendEmailRequest> captor =
                ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest request = captor.getValue();

        assertThat(messageId).isEqualTo("ses-message-id");
        assertThat(request.source()).isEqualTo("no-reply@dropit.example");
        assertThat(request.destination().toAddresses()).containsExactly("buyer@example.com");
        assertThat(request.message().subject().charset()).isEqualTo("UTF-8");
        assertThat(request.message().body().html().data()).contains("한정판 후드티");
    }

    @Test
    void 발신_주소가_없으면_SES를_호출하지_않는다() {
        AwsSesPurchaseEmailSender sender = new AwsSesPurchaseEmailSender(
                sesClient,
                new EmailSesProperties(""),
                template
        );

        assertThatThrownBy(() -> sender.send(message("buyer@example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("구매 성공 이메일 발신 주소가 설정되지 않았습니다.");
        verifyNoInteractions(sesClient);
    }

    private PurchaseCompletedMessage message(String recipientEmail) {
        return new PurchaseCompletedMessage(
                PurchaseCompletedMessage.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                10L,
                1L,
                recipientEmail,
                "로컬구매자",
                "한정판 후드티",
                1,
                new BigDecimal("49000"),
                Instant.parse("2026-09-16T02:00:00Z")
        );
    }
}
