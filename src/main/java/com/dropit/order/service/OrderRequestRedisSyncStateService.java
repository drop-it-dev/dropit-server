package com.dropit.order.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.order.entity.OrderRequest;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.order.redis.OrderRequestSyncCommand;
import com.dropit.order.repository.OrderRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderRequestRedisSyncStateService {

    private final OrderRequestRepository orderRequestRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<OrderRequestSyncCommand> loadPending(UUID requestId) {
        OrderRequest request = findForUpdate(requestId);
        if (!request.isRedisSyncPending()) {
            return Optional.empty();
        }

        return Optional.of(new OrderRequestSyncCommand(
                request.getId(),
                request.getDesiredRedisState(),
                request.getOrderId(),
                request.getUserId(),
                request.getDropId(),
                request.getIdempotencyKeyHash(),
                request.getQuantity(),
                request.getFailureCode(),
                request.getDesiredVersion(),
                request.getRedisExpiresAt()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearPending(UUID requestId, long desiredVersion) {
        OrderRequest request = findForUpdate(requestId);
        request.completeRedisSync(desiredVersion);
    }

    private OrderRequest findForUpdate(UUID requestId) {
        return orderRequestRepository.findByRequestIdForUpdate(requestId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_REQUEST_NOT_FOUND));
    }
}
