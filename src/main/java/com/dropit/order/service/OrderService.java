package com.dropit.order.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.order.dto.response.OrderResponse;
import com.dropit.order.dto.response.OrderSummaryResponse;
import com.dropit.order.entity.Order;
import com.dropit.order.entity.OrderItem;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.order.repository.OrderItemRepository;
import com.dropit.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

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

}
