package com.dropit.order.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class OrderSqsListenerConfigTest {

    private final OrderSqsListenerConfig config = new OrderSqsListenerConfig();
    private final SqsAsyncClient client = mock(SqsAsyncClient.class);

    @Test
    @DisplayName("Consumer가 꺼져 있어도 Producer가 사용할 queue URL은 필수다")
    void requireQueueUrlWhenConsumerIsDisabled() {
        assertThrows(IllegalArgumentException.class,
                () -> config.orderSqsListenerContainerFactory(client, properties(false, "", 0, -1, 43_201)));
    }

    @Test
    @DisplayName("Consumer가 꺼져 있으면 수신 전용 설정 범위는 검사하지 않는다")
    void ignoreReceivePropertiesWhenConsumerIsDisabled() {
        assertDoesNotThrow(() -> config.orderSqsListenerContainerFactory(
                client, properties(false, "queue-url", 0, -1, 43_201)));
    }

    @ParameterizedTest
    @MethodSource("invalidActiveProperties")
    @DisplayName("활성 Consumer의 SQS 수신 설정이 유효하지 않으면 시작에 실패한다")
    void rejectInvalidActiveConsumerProperties(SqsProperties properties) {
        assertThrows(IllegalArgumentException.class,
                () -> config.orderSqsListenerContainerFactory(client, properties));
    }

    private static Stream<SqsProperties> invalidActiveProperties() {
        return Stream.of(
                properties(true, "queue-url", 0, 0, 0),
                properties(true, "queue-url", 11, 0, 0),
                properties(true, "queue-url", 1, -1, 0),
                properties(true, "queue-url", 1, 21, 0),
                properties(true, "queue-url", 1, 0, -1),
                properties(true, "queue-url", 1, 0, 43_201)
        );
    }

    private static SqsProperties properties(
            boolean enabled, String queueUrl, int maxMessages, int waitSeconds, int visibilitySeconds
    ) {
        return new SqsProperties(queueUrl, enabled, 1, maxMessages, waitSeconds, visibilitySeconds, 3_000);
    }
}
