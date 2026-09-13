package com.dropit.order.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderSqsConsumerConfigTest {

    private final OrderSqsConsumerConfig config = new OrderSqsConsumerConfig();

    @ParameterizedTest
    @MethodSource("invalidActiveProperties")
    @DisplayName("활성화된 Consumer의 SQS 수신 설정이 유효하지 않으면 시작에 실패한다")
    void rejectInvalidActiveConsumerProperties(SqsProperties properties) {
        assertThrows(IllegalArgumentException.class, () -> config.sqsOrderConsumerExecutor(properties));
    }

    @Test
    @DisplayName("비활성화된 Consumer는 SQS 수신 설정을 사용하지 않는다")
    void ignoreReceivePropertiesWhenConsumerIsDisabled() {
        SqsProperties properties = properties(false, "", 0, -1, 43_201);

        ThreadPoolExecutor executor = assertDoesNotThrow(
                () -> config.sqsOrderConsumerExecutor(properties)
        );
        executor.shutdown();
    }

    private static Stream<SqsProperties> invalidActiveProperties() {
        return Stream.of(
                properties(true, "", 1, 0, 0),
                properties(true, "queue-url", 0, 0, 0),
                properties(true, "queue-url", 11, 0, 0),
                properties(true, "queue-url", 1, -1, 0),
                properties(true, "queue-url", 1, 21, 0),
                properties(true, "queue-url", 1, 0, -1),
                properties(true, "queue-url", 1, 0, 43_201)
        );
    }

    private static SqsProperties properties(
            boolean enabled,
            String queueUrl,
            int maxNumberOfMessages,
            int waitTimeSeconds,
            int visibilityTimeoutSeconds
    ) {
        return new SqsProperties(
                queueUrl,
                enabled,
                1,
                maxNumberOfMessages,
                waitTimeSeconds,
                visibilityTimeoutSeconds,
                1_000,
                1_000
        );
    }
}
