package com.dropit.order.service;

import com.dropit.drop.entity.Drop;
import com.dropit.drop.exception.DropErrorCode;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.exception.CommonErrorCode;
import com.dropit.global.exception.ServiceException;
import com.dropit.order.entity.Order;
import com.dropit.order.entity.OrderItem;
import com.dropit.order.entity.OrderRequest;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.order.repository.DropUserPurchaseRepository;
import com.dropit.order.repository.OrderItemRepository;
import com.dropit.order.repository.OrderRepository;
import com.dropit.order.repository.OrderRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final OrderRequestRepository requestRepository;
    private final OrderItemRepository itemRepository;
    private final DropUserPurchaseRepository purchaseRepository;
    private final DropRepository dropRepository;
    private final OrderRequestRedisSyncService redisSyncService;
    private final TransactionTemplate transactionTemplate;

    public void cancel(Long userId, Long orderId) {
        OrderRequest request = transactionTemplate.execute(status -> cancelInTransaction(userId, orderId));
        if (request == null) {
            return;
        }
        try {
            redisSyncService.sync(request.getId());
        } catch (RuntimeException exception) {
            log.warn("주문 취소 Redis 동기화를 지연 처리합니다. requestId={}", request.getId(), exception);
        }
    }

    private OrderRequest cancelInTransaction(Long userId, Long orderId) {
        OrderRequest request = requestRepository.findByOrderIdForUpdate(orderId).orElse(null);
        Order order = orderRepository.findByIdAndUser_IdForUpdate(orderId, userId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        List<OrderItem> items = itemRepository.findAllByOrder_IdOrderByIdAsc(orderId);
        order.cancel();
        for (OrderItem item : items) {
            Long dropId = item.getDrop().getId();
            purchaseRepository.findByDropIdAndUserIdForUpdate(dropId, userId)
                    .orElseThrow(() -> new ServiceException(CommonErrorCode.INTERNAL_SERVER_ERROR));
            Drop drop = dropRepository.findByIdForUpdate(dropId)
                    .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));
            if (purchaseRepository.decreaseConfirmedQuantity(dropId, userId, item.getQuantity()) != 1) {
                throw new ServiceException(CommonErrorCode.INTERNAL_SERVER_ERROR);
            }
            drop.restoreStock(item.getQuantity());
        }
        if (request != null) {
            request.requestCancellationSync();
        }
        return request;
    }
}
