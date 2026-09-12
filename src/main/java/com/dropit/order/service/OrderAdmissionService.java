package com.dropit.order.service;

import com.dropit.order.redis.OrderAdmissionRequest;
import com.dropit.order.redis.OrderAdmissionResult;
import com.dropit.order.redis.RedisOrderAdmissionAdapter;
import com.dropit.order.validation.OrderAdmissionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Redis Lua로 주문 요청을 원자적으로 검증 및 예약하고 중복 요청 결과를 반환 */
@Service
@RequiredArgsConstructor
public class OrderAdmissionService {

    private final RedisOrderAdmissionAdapter redisAdapter;

    public OrderAdmissionResult reserve(
            Long userId,
            Long dropId,
            String idempotencyKeyHash,
            int quantity,
            int purchaseLimit
    ) {
        return reserve(OrderAdmissionRequest.create(
                userId,
                dropId,
                idempotencyKeyHash,
                quantity,
                purchaseLimit
        ));
    }

    public OrderAdmissionResult reserve(OrderAdmissionRequest request) {
        OrderAdmissionValidator.validate(request);
        return redisAdapter.reserve(request);
    }
}
