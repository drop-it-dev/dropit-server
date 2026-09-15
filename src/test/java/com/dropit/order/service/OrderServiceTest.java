package com.dropit.order.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.order.dto.response.OrderResponse;
import com.dropit.order.dto.response.OrderSummaryResponse;
import com.dropit.order.entity.Order;
import com.dropit.order.entity.OrderStatus;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.order.repository.OrderItemRepository;
import com.dropit.order.repository.OrderRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @InjectMocks private OrderService orderService;

    @Test
    @DisplayName("내 주문 목록은 최신순 조회 결과를 반환한다")
    void getMyOrders() {
        Order order = order(1000L);
        when(orderRepository.findAllByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(order));

        List<OrderSummaryResponse> responses = orderService.getMyOrders(1L);

        assertEquals(1, responses.size());
        assertEquals(1000L, responses.getFirst().id());
    }

    @Test
    @DisplayName("본인 소유가 아닌 주문 상세는 노출하지 않는다")
    void rejectAnotherUsersOrder() {
        when(orderRepository.findByIdAndUser_Id(1000L, 2L)).thenReturn(Optional.empty());

        ServiceException exception = assertThrows(
                ServiceException.class, () -> orderService.getMyOrder(2L, 1000L));

        assertEquals(OrderErrorCode.ORDER_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(orderItemRepository);
    }

    @Test
    @DisplayName("내 주문 상세는 저장된 주문 항목과 함께 반환한다")
    void getMyOrder() {
        Order order = order(1000L);
        when(orderRepository.findByIdAndUser_Id(1000L, 1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_IdOrderByIdAsc(1000L)).thenReturn(List.of());

        OrderResponse response = orderService.getMyOrder(1L, 1000L);

        assertEquals(1000L, response.id());
        assertEquals(OrderStatus.ORDERED, response.status());
    }

    private Order order(Long id) {
        User buyer = new User("buyer@example.com", "password", "buyer", UserRole.USER);
        ReflectionTestUtils.setField(buyer, "id", 1L);
        Order order = new Order(buyer, new BigDecimal("59000"));
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
