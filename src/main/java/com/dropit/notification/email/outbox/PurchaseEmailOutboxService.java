package com.dropit.notification.email.outbox;

import com.dropit.notification.email.messaging.PurchaseCompletedMessage;
import com.dropit.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseEmailOutboxService {

    private final PurchaseEmailOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void savePurchaseCompleted(
            Long orderId,
            User user,
            String productName,
            int quantity,
            BigDecimal totalPrice
    ) {
        UUID eventId = UUID.randomUUID();
        Instant completedAt = Instant.now();

        PurchaseCompletedMessage message = new PurchaseCompletedMessage(
                PurchaseCompletedMessage.CURRENT_SCHEMA_VERSION,
                eventId,
                orderId,
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                productName,
                quantity,
                totalPrice,
                completedAt
        );

        String payload = objectMapper.writeValueAsString(message);

        outboxRepository.save(new PurchaseEmailOutbox(
                eventId,
                orderId,
                payload,
                completedAt
        ));
    }
}
