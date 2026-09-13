package com.dropit.order.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.order.dto.response.OrderRequestStatusResponse;
import com.dropit.order.entity.OrderRequest;
import com.dropit.order.entity.OrderRequestStatus;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.order.redis.RedisOrderAdmissionAdapter;
import com.dropit.order.redis.ReservedOrderSnapshot;
import com.dropit.order.repository.OrderRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderRequestQueryService {

    private static final Set<String> EXPOSED_FAILURE_CODES = Set.of(
            "INSUFFICIENT_STOCK", "PURCHASE_LIMIT_EXCEEDED", "DROP_NOT_OPEN"
    );

    private final OrderRequestRepository repository;
    private final RedisOrderAdmissionAdapter redisAdapter;

    @Transactional(readOnly = true)
    public OrderRequestStatusResponse get(Long userId, UUID requestId) {
        Optional<OrderRequest> stored;
        try {
            stored = repository.findByIdAndUserId(requestId, userId);
        } catch (RuntimeException exception) {
            throw new ServiceException(OrderErrorCode.ORDER_ADMISSION_NOT_READY, exception);
        }
        if (stored.isPresent() && stored.get().isTerminal()) {
            OrderRequest request = stored.get();
            return new OrderRequestStatusResponse(
                    request.getId(), request.getStatus(), request.getOrderId(),
                    request.getOrder() == null ? null : request.getOrder().getStatus(),
                    exposedFailureCode(request.getFailureCode())
            );
        }

        Optional<ReservedOrderSnapshot> pending;
        try {
            pending = redisAdapter.findByRequestId(requestId, userId);
        } catch (RuntimeException exception) {
            throw new ServiceException(OrderErrorCode.ORDER_ADMISSION_NOT_READY, exception);
        }
        if (pending.isPresent()) {
            ReservedOrderSnapshot snapshot = pending.get();
            return new OrderRequestStatusResponse(
                    snapshot.requestId(), snapshot.status(), snapshot.orderId(), null,
                    exposedFailureCode(snapshot.failureCode())
            );
        }
        if (stored.isPresent()) {
            throw new ServiceException(OrderErrorCode.ORDER_ADMISSION_NOT_READY);
        }
        throw new ServiceException(OrderErrorCode.ORDER_REQUEST_NOT_FOUND);
    }

    private String exposedFailureCode(String failureCode) {
        if (failureCode == null) {
            return null;
        }
        return EXPOSED_FAILURE_CODES.contains(failureCode) ? failureCode : "ORDER_FINALIZATION_FAILED";
    }
}
