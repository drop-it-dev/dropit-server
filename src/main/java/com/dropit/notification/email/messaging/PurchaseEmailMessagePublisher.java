package com.dropit.notification.email.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class PurchaseEmailMessagePublisher {

    private final SqsAsyncClient sqsClient;
    private final EmailSqsProperties properties;

    public String publish(String payload) {
        validateConfiguration();

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .messageBody(payload)
                .build();

        SendMessageResponse response = sqsClient.sendMessage(request)
                .orTimeout(properties.publisherTimeoutMillis(), TimeUnit.MILLISECONDS)
                .join();

        if (response.messageId() == null || response.messageId().isBlank()) {
            throw new IllegalStateException("SQS가 구매 성공 이메일 메시지 ID를 반환하지 않았습니다.");
        }

        return response.messageId();
    }

    private void validateConfiguration() {
        if (properties.queueUrl() == null || properties.queueUrl().isBlank()) {
            throw new IllegalStateException("구매 성공 이메일 SQS 큐 URL이 설정되지 않았습니다.");
        }
        if (properties.publisherTimeoutMillis() <= 0) {
            throw new IllegalStateException("구매 성공 이메일 SQS 전송 제한시간은 0보다 커야 합니다.");
        }
    }
}
