package com.dropit.order.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class OrderMessagePublisher {

    private final SqsAsyncClient sqsClient;
    private final SqsProperties properties;
    private final ObjectMapper objectMapper;

    public String publish(OrderMessage message) {
        if (properties.orderQueueUrl() == null || properties.orderQueueUrl().isBlank()) {
            throw new IllegalStateException("주문 SQS 큐 URL이 설정되지 않았습니다.");
        }
        SendMessageResponse response = sqsClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(properties.orderQueueUrl())
                        .messageBody(objectMapper.writeValueAsString(message))
                        .build())
                .orTimeout(properties.publisherTimeoutMillis(), TimeUnit.MILLISECONDS)
                .join();
        if (response.messageId() == null || response.messageId().isBlank()) {
            throw new IllegalStateException("SQS가 주문 메시지 ID를 반환하지 않았습니다.");
        }
        return response.messageId();
    }
}
