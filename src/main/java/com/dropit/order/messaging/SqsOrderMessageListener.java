package com.dropit.order.messaging;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SqsOrderMessageListener {

    private final OrderMessageProcessor messageProcessor;

    @SqsListener(
            value = "${app.order.sqs.order-queue-url}",
            factory = "orderSqsListenerContainerFactory"
    )
    public void listen(OrderMessage message, Acknowledgement acknowledgement) {
        messageProcessor.process(message);
        acknowledgement.acknowledge();
    }
}
