package com.dropit.order.service;

import com.dropit.drop.entity.Drop;
import com.dropit.drop.exception.DropErrorCode;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.exception.ServiceException;
import com.dropit.order.entity.Order;
import com.dropit.order.entity.OrderItem;
import com.dropit.order.entity.OrderRequest;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.order.repository.DropUserPurchaseRepository;
import com.dropit.order.repository.OrderItemRepository;
import com.dropit.order.repository.OrderRepository;
import com.dropit.order.repository.OrderRequestRepository;
import com.dropit.user.entity.User;
import com.dropit.user.exception.UserErrorCode;
import com.dropit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Consumer가 전달한 주문 요청을 MySQL 트랜잭션으로 중복 없이 최종 주문 처리
 */
@Service
@RequiredArgsConstructor
public class OrderFinalizationService {

    private final OrderRequestRepository orderRequestRepository;
    private final DropUserPurchaseRepository dropUserPurchaseRepository;
    private final DropRepository dropRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public void finalizeOrder(UUID requestId) {
        // 1. 중복 orderRequest 처리 방지
        OrderRequest request = orderRequestRepository.findByRequestIdForUpdate(requestId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_REQUEST_NOT_FOUND));
        if (request.isTerminal()) {
            return;
        }

        // 2. 주문 확정용 user, drop 조회
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ServiceException(UserErrorCode.USER_NOT_FOUND));
        Drop drop = dropRepository.findById(request.getDropId())
                .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));

        // 3. 인당 한도 내 구매량 증가 및 조건부 재고 차감
        reservePurchaseLimit(user.getId(), drop, request.getQuantity());
        decreaseStock(drop.getId(), request.getQuantity());

        // 4. Order, OrderItem 생성 및 주문 요청 확정
        BigDecimal totalPrice = OrderItem.calculateItemTotalPrice(
                request.getUnitPrice(),
                request.getDiscountRate(),
                request.getQuantity()
        );

        Order order = orderRepository.save(new Order(user, totalPrice));
        orderItemRepository.save(new OrderItem(
                order,
                drop,
                request.getProductName(),
                request.getUnitPrice(),
                request.getDiscountRate(),
                request.getQuantity()
        ));

        request.succeed(order);
    }

    private void reservePurchaseLimit(Long userId, Drop drop, int quantity) {
        dropUserPurchaseRepository.createCounterIfAbsent(drop.getId(), userId);

        int increasedCount = dropUserPurchaseRepository.increaseWithinLimit(
                drop.getId(),
                userId,
                quantity,
                drop.getPurchaseLimit()
        );
        if (increasedCount != 1) {
            throw new ServiceException(OrderErrorCode.PURCHASE_LIMIT_EXCEEDED);
        }
    }

    private void decreaseStock(Long dropId, int quantity) {
        int decreasedCount = dropRepository.decreaseStockIfAvailable(
                dropId,
                quantity
        );
        if (decreasedCount != 1) {
            throw new ServiceException(DropErrorCode.INSUFFICIENT_STOCK);
        }
    }
}
