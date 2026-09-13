package com.dropit.order.messaging;

import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SqsOrderMessageListenerTest {

    private final OrderMessageProcessor processor = mock(OrderMessageProcessor.class);
    private final Acknowledgement acknowledgement = mock(Acknowledgement.class);
    private final SqsOrderMessageListener listener = new SqsOrderMessageListener(processor);

    @Test
    @DisplayName("DB와 Redis 처리가 완료된 뒤에만 ACK한다")
    void acknowledgeAfterProcessing() {
        OrderMessage message = mock(OrderMessage.class);

        listener.listen(message, acknowledgement);

        var order = inOrder(processor, acknowledgement);
        order.verify(processor).process(message);
        order.verify(acknowledgement).acknowledge();
    }

    @Test
    @DisplayName("처리 실패는 ACK하지 않고 listener 밖으로 전달한다")
    void doNotAcknowledgeProcessingFailure() {
        OrderMessage message = mock(OrderMessage.class);
        doThrow(new IllegalStateException("redis unavailable")).when(processor).process(message);

        assertThrows(IllegalStateException.class, () -> listener.listen(message, acknowledgement));

        verifyNoInteractions(acknowledgement);
    }
}
