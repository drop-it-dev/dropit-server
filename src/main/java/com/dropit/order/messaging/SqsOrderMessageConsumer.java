package com.dropit.order.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsOrderMessageConsumer {

    private final SqsClient sqsClient;
    private final SqsProperties properties;
    private final ObjectMapper objectMapper;
    private final OrderMessageProcessor messageProcessor;

    @Scheduled(
            initialDelayString = "${app.order.sqs.consumer-initial-delay}",
            fixedDelayString = "${app.order.sqs.consumer-poll-delay}"
    )
    public void poll() {
        if (!properties.consumerEnabled()
                || properties.orderQueueUrl() == null
                || properties.orderQueueUrl().isBlank()) {
            return;
        }

        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(properties.orderQueueUrl())
                .maxNumberOfMessages(properties.consumerMaxNumberOfMessages())
                .waitTimeSeconds(properties.consumerWaitTimeSeconds())
                .visibilityTimeout(properties.consumerVisibilityTimeoutSeconds())
                .build();

        try {
            for (Message message : sqsClient.receiveMessage(request).messages()) {
                process(message);
            }
        } catch (RuntimeException exception) {
            log.warn("주문 SQS 수신에 실패했습니다. 메시지를 삭제하지 않고 다음 poll을 기다립니다.", exception);
        }
    }

    private void process(Message sqsMessage) {
        try {
            OrderMessage orderMessage = objectMapper.readValue(sqsMessage.body(), OrderMessage.class);
            messageProcessor.process(orderMessage);
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(properties.orderQueueUrl())
                    .receiptHandle(sqsMessage.receiptHandle())
                    .build());
        } catch (RuntimeException exception) {
            log.warn("주문 SQS 메시지 처리에 실패했습니다. messageId={}", sqsMessage.messageId(), exception);
        }
    }
}
