package com.dropit.notification.email.messaging;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PurchaseEmailSqsListenerConfigTest {

    private final PurchaseEmailSqsListenerConfig config =
            new PurchaseEmailSqsListenerConfig();
    private final SqsAsyncClient sqsClient = mock(SqsAsyncClient.class);

    @Test
    void 유효한_수신_설정으로_Listener_Container를_생성한다() {
        assertThatCode(() -> config.purchaseEmailSqsListenerContainerFactory(
                sqsClient,
                properties("https://sqs.ap-northeast-2.amazonaws.com/123/purchase-email", 2, 2, 10, 60)
        )).doesNotThrowAnyException();
    }

    @Test
    void 잘못된_수신_설정은_Listener_Container_생성을_차단한다() {
        Stream.of(
                properties("", 2, 2, 10, 60),
                properties("queue-url", 0, 2, 10, 60),
                properties("queue-url", 2, 11, 10, 60),
                properties("queue-url", 2, 2, 21, 60),
                properties("queue-url", 2, 2, 10, 0)
        ).forEach(properties -> assertThatThrownBy(
                () -> config.purchaseEmailSqsListenerContainerFactory(sqsClient, properties)
        ).isInstanceOf(IllegalArgumentException.class));
    }

    private EmailSqsProperties properties(
            String queueUrl,
            int concurrency,
            int maxMessages,
            int waitSeconds,
            int visibilitySeconds
    ) {
        return new EmailSqsProperties(
                queueUrl,
                true,
                3000,
                50,
                30000,
                5000,
                true,
                concurrency,
                maxMessages,
                waitSeconds,
                visibilitySeconds
        );
    }
}
