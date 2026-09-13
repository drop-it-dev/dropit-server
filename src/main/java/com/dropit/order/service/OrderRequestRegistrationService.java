package com.dropit.order.service;

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(OrderMessage message) {
        OrderMessageValidator.validate(message);

        OrderRequest existing = orderRequestRepository.findById(message.requestId()).orElse(null);
        if (existing != null) {
            OrderMessageValidator.validateSameRequest(existing, message);
            return;
        }

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
                OrderMessageValidator.acceptedAt(message)
        ));
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void verifyExisting(OrderMessage message) {
        OrderRequest existing = orderRequestRepository.findById(message.requestId())
                .orElseThrow(() -> new InvalidOrderMessageException("중복된 주문 요청 원장을 찾을 수 없습니다."));
        OrderMessageValidator.validateSameRequest(existing, message);
    }
}
