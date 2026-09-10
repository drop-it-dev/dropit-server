package com.dropit.order.service;

import com.dropit.drop.entity.Drop;
import com.dropit.drop.exception.DropErrorCode;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.exception.CommonErrorCode;
import com.dropit.global.exception.ServiceException;
import com.dropit.order.dto.request.OrderCreateRequest;
import com.dropit.order.dto.request.OrderItemCreateRequest;
import com.dropit.order.dto.response.OrderResponse;
import com.dropit.order.dto.response.OrderSummaryResponse;
import com.dropit.order.entity.Order;
import com.dropit.order.entity.OrderItem;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.order.repository.DropUserPurchaseRepository;
import com.dropit.order.repository.OrderItemRepository;
import com.dropit.order.repository.OrderRepository;
import com.dropit.user.entity.User;
import com.dropit.user.exception.UserErrorCode;
import com.dropit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final UserRepository userRepository;
    private final DropRepository dropRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DropUserPurchaseRepository dropUserPurchaseRepository;

    @Transactional
    public OrderResponse create(Long userId, OrderCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(UserErrorCode.USER_NOT_FOUND));

        OrderItemCreateRequest itemRequest = request.items().getFirst();
        Drop drop = dropRepository.findByIdForUpdate(itemRequest.dropId())
                .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        reservePurchaseLimit(userId, drop, itemRequest.quantity());
        drop.decreaseStock(itemRequest.quantity(), now);
        BigDecimal totalPrice = OrderItem.calculateItemTotalPrice(
                drop.getPrice(),
                drop.getDiscountRate(),
                itemRequest.quantity()
        );

        Order order = orderRepository.save(new Order(user, totalPrice));
        List<OrderItem> orderItems = List.of(createOrderItem(order, drop, itemRequest.quantity()));
        List<OrderItem> savedOrderItems = orderItemRepository.saveAll(orderItems);

        return OrderResponse.from(order, savedOrderItems);
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getMyOrders(Long userId) {
        return orderRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(OrderSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUser_Id(orderId, userId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        List<OrderItem> orderItems = orderItemRepository.findAllByOrder_IdOrderByIdAsc(orderId);

        return OrderResponse.from(order, orderItems);
    }

    @Transactional
    public void cancel(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUser_IdForUpdate(orderId, userId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        List<OrderItem> orderItems = orderItemRepository.findAllByOrder_IdOrderByIdAsc(orderId);

        order.cancel();
        for (OrderItem orderItem : orderItems) {
            Drop drop = dropRepository.findByIdForUpdate(orderItem.getDrop().getId())
                    .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));
            drop.restoreStock(orderItem.getQuantity());
            int decreasedCount = dropUserPurchaseRepository.decreaseConfirmedQuantity(
                    drop.getId(),
                    userId,
                    orderItem.getQuantity()
            );
            if (decreasedCount != 1) {
                // 구매 수량 카운터가 정상적으로 차감되지 않으면 취소 전체를 롤백한다.
                throw new ServiceException(CommonErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private void reservePurchaseLimit(Long userId, Drop drop, int quantity) {
        dropUserPurchaseRepository.createCounterIfAbsent(drop.getId(), userId);
        int increasedCount = dropUserPurchaseRepository.increaseWithinLimit(
                drop.getId(),
                userId,
                quantity,
                drop.getPurchaseLimit()
        );
        if (increasedCount == 0) {
            throw new ServiceException(OrderErrorCode.PURCHASE_LIMIT_EXCEEDED);
        }
    }

    private OrderItem createOrderItem(Order order, Drop drop, int quantity) {
        return new OrderItem(
                order,
                drop,
                drop.getProduct().getName(),
                drop.getPrice(),
                drop.getDiscountRate(),
                quantity
        );
    }
}
