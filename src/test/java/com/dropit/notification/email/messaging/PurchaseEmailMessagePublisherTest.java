package com.dropit.notification.email.messaging;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PurchaseEmailMessagePublisherTest {

    private final SqsAsyncClient sqsClient = mock(SqsAsyncClient.class);

    @Test
    void 구매_성공_이메일_메시지를_SQS로_발행한다() {
        EmailSqsProperties properties = new EmailSqsProperties(
                "https://sqs.ap-northeast-2.amazonaws.com/123/purchase-email",
                true,
                3000,
                50,
                30000,
                5000
        );
        PurchaseEmailMessagePublisher publisher =
                new PurchaseEmailMessagePublisher(sqsClient, properties);
        SendMessageResponse response = SendMessageResponse.builder()
                .messageId("message-1")
                .build();
        when(sqsClient.sendMessage(org.mockito.ArgumentMatchers.any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        String messageId = publisher.publish("{\"orderId\":1}");

        ArgumentCaptor<SendMessageRequest> captor =
                ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(captor.capture());
        assertThat(captor.getValue().queueUrl()).isEqualTo(properties.queueUrl());
        assertThat(captor.getValue().messageBody()).isEqualTo("{\"orderId\":1}");
        assertThat(messageId).isEqualTo("message-1");
    }

    @Test
    void 이메일_큐_URL이_없으면_발행하지_않는다() {
        EmailSqsProperties properties = new EmailSqsProperties(
                "",
                true,
                3000,
                50,
                30000,
                5000
        );
        PurchaseEmailMessagePublisher publisher =
                new PurchaseEmailMessagePublisher(sqsClient, properties);

        assertThatThrownBy(() -> publisher.publish("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("구매 성공 이메일 SQS 큐 URL이 설정되지 않았습니다.");
        verifyNoInteractions(sqsClient);
    }
}
