package com.dropit.order.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.order.entity.OrderRequest;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.order.redis.OrderRequestSyncCommand;
import com.dropit.order.redis.OrderRequestSyncResult;
import com.dropit.order.redis.RedisOrderFinalizationSyncAdapter;
import com.dropit.order.repository.OrderRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderRequestRedisSyncService {

    private final OrderRequestRepository orderRequestRepository;
    private final RedisOrderFinalizationSyncAdapter syncAdapter;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sync(UUID requestId) {
        OrderRequest request = orderRequestRepository.findByRequestIdForUpdate(requestId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_REQUEST_NOT_FOUND));
        if (!request.isRedisSyncPending()) {
            return;
        }

        OrderRequestSyncResult result = syncAdapter.sync(new OrderRequestSyncCommand(
                request.getId(),
                request.getStatus(),
                request.getOrderId(),
                request.getUserId(),
                request.getDropId(),
                request.getIdempotencyKeyHash(),
                request.getQuantity(),
                request.getFailureCode(),
                request.getDesiredVersion()
        ));
        if (result != OrderRequestSyncResult.APPLIED
                && result != OrderRequestSyncResult.ALREADY_APPLIED) {
            throw new IllegalStateException("Redis 주문 최종 상태를 반영할 수 없습니다: " + result);
        }
        request.completeRedisSync(request.getDesiredVersion());
    }
}
