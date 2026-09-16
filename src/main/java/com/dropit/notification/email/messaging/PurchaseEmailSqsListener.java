package com.dropit.notification.email.messaging;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.notification.email.sqs",
        name = "consumer-enabled",
        havingValue = "true"
)
public class PurchaseEmailSqsListener {

    private final PurchaseEmailMessageProcessor messageProcessor;

    @SqsListener(
            value = "${app.notification.email.sqs.queue-url}",
            factory = "purchaseEmailSqsListenerContainerFactory"
    )
    public void listen(
            PurchaseCompletedMessage message,
            Acknowledgement acknowledgement
    ) {
        messageProcessor.process(message);
        acknowledgement.acknowledge();
    }
}
