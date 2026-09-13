package com.dropit.order.service;

import com.dropit.drop.entity.Drop;
import com.dropit.drop.exception.DropErrorCode;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.exception.ServiceException;
import com.dropit.order.entity.OrderRequest;
import com.dropit.order.messaging.InvalidOrderMessageException;
import com.dropit.order.messaging.OrderMessage;
import com.dropit.order.repository.OrderRequestRepository;
import com.dropit.order.validation.OrderMessageValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderRequestRegistrationService {

    private final OrderRequestRepository orderRequestRepository;
    private final DropRepository dropRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(OrderMessage message) {
        OrderMessageValidator.validate(message);

        OrderRequest existing = orderRequestRepository.findById(message.requestId()).orElse(null);
        if (existing != null) {
            OrderMessageValidator.validateSameRequest(existing, message);
            return;
        }

        Drop drop = dropRepository.findById(message.dropId())
                .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));
        java.time.Instant redisExpiresAt = drop.getCloseAt()
                .atZone(java.time.ZoneId.of("Asia/Seoul"))
                .toInstant()
                .plus(java.time.Duration.ofHours(24));
        orderRequestRepository.saveAndFlush(new OrderRequest(
                message.requestId(),
                message.userId(),
                message.dropId(),
                message.idempotencyKeyHash(),
                message.requestFingerprint(),
                message.productName(),
                message.unitPrice(),
                message.quantity(),
                message.discountRate(),
                OrderMessageValidator.acceptedAt(message),
                redisExpiresAt
        ));
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void verifyExisting(OrderMessage message) {
        OrderRequest existing = orderRequestRepository.findById(message.requestId())
                .orElseThrow(() -> new InvalidOrderMessageException("중복된 주문 요청 원장을 찾을 수 없습니다."));
        OrderMessageValidator.validateSameRequest(existing, message);
    }
}
