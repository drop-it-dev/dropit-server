package com.dropit.order.service;

import com.dropit.order.redis.OrderRequestSyncCommand;
import com.dropit.order.redis.OrderRequestSyncResult;
import com.dropit.order.redis.RedisOrderFinalizationSyncAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OrderRequestRedisSyncService {

    private final OrderRequestRedisSyncStateService stateService;
    private final RedisOrderFinalizationSyncAdapter syncAdapter;

    public void sync(UUID requestId) {
        Optional<OrderRequestSyncCommand> pendingCommand = stateService.loadPending(requestId);
        if (pendingCommand.isEmpty()) {
            return;
        }

        OrderRequestSyncCommand command = pendingCommand.get();
        OrderRequestSyncResult result = syncAdapter.sync(command);
        if (result == OrderRequestSyncResult.MISSING && !Instant.now().isBefore(command.expiresAt())) {
            stateService.clearPending(command.requestId(), command.desiredVersion());
            return;
        }
        if (result != OrderRequestSyncResult.APPLIED
                && result != OrderRequestSyncResult.ALREADY_APPLIED) {
            throw new IllegalStateException("Redis 주문 최종 상태를 반영할 수 없습니다: " + result);
        }
        stateService.clearPending(command.requestId(), command.desiredVersion());
    }
}
