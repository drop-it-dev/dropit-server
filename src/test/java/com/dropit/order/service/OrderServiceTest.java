package com.dropit.order.service;

import com.dropit.drop.entity.Drop;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.exception.ServiceException;
import com.dropit.order.dto.request.OrderCreateRequest;
import com.dropit.order.dto.request.OrderItemCreateRequest;
import com.dropit.order.dto.response.OrderResponse;
import com.dropit.order.dto.response.OrderSummaryResponse;
import com.dropit.order.entity.Order;
import com.dropit.order.entity.OrderItem;
import com.dropit.order.entity.OrderStatus;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.order.repository.OrderItemRepository;
import com.dropit.order.repository.OrderRepository;
import com.dropit.product.entity.Product;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DropRepository dropRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @InjectMocks private OrderService orderService;

    @Test
    @DisplayName("주문을 생성하면 재고를 차감하고 주문 시점 정보를 저장한다")
    void createOrder() {
        User buyer = createUser(1L, UserRole.USER);
        Drop drop = createOpenDrop(10L, 100L, 10, 20, 2);
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemCreateRequest(100L, 2)));

        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(dropRepository.findById(100L)).thenReturn(Optional.of(drop));
        when(orderItemRepository.sumQuantityByUserAndDropAndStatus(1L, 100L, OrderStatus.ORDERED))
                .thenReturn(0L);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 1000L);
            return order;
        });
        when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.create(1L, request);

        assertEquals(1000L, response.id());
        assertEquals(OrderStatus.ORDERED, response.status());
        assertEquals(new BigDecimal("94400"), response.totalPrice());
        assertEquals(8, drop.getRemainingQuantity());
        assertEquals(1, response.items().size());
        assertEquals("Limited Hoodie", response.items().getFirst().productName());
        assertEquals(new BigDecimal("59000"), response.items().getFirst().unitPrice());
        assertEquals(20, response.items().getFirst().discountRate());
        assertEquals(new BigDecimal("94400"), response.items().getFirst().itemTotalPrice());
    }

    @Test
    @DisplayName("기존 구매 수량과 새 수량의 합이 구매 제한을 넘으면 주문할 수 없다")
    void rejectPurchaseLimitExceeded() {
        User buyer = createUser(1L, UserRole.USER);
        Drop drop = createOpenDrop(10L, 100L, 10, 20, 2);
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemCreateRequest(100L, 2)));

        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(dropRepository.findById(100L)).thenReturn(Optional.of(drop));
        when(orderItemRepository.sumQuantityByUserAndDropAndStatus(1L, 100L, OrderStatus.ORDERED))
                .thenReturn(1L);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> orderService.create(1L, request)
        );

        assertEquals(OrderErrorCode.PURCHASE_LIMIT_EXCEEDED, exception.getErrorCode());
        assertEquals(10, drop.getRemainingQuantity());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("내 주문 목록을 최신순 조회 결과로 반환한다")
    void getMyOrders() {
        User buyer = createUser(1L, UserRole.USER);
        Order order = new Order(buyer, new BigDecimal("94400"));
        ReflectionTestUtils.setField(order, "id", 1000L);
        when(orderRepository.findAllByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(order));

        List<OrderSummaryResponse> responses = orderService.getMyOrders(1L);

        assertEquals(1, responses.size());
        assertEquals(1000L, responses.getFirst().id());
        assertEquals(new BigDecimal("94400"), responses.getFirst().totalPrice());
    }

    @Test
    @DisplayName("내 주문 상세는 주문 항목을 함께 반환한다")
    void getMyOrder() {
        User buyer = createUser(1L, UserRole.USER);
        Drop drop = createOpenDrop(10L, 100L, 10, 20, 2);
        Order order = new Order(buyer, new BigDecimal("94400"));
        ReflectionTestUtils.setField(order, "id", 1000L);
        OrderItem orderItem = new OrderItem(order, drop, "Limited Hoodie", new BigDecimal("59000"), 20, 2);

        when(orderRepository.findByIdAndUser_Id(1000L, 1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_IdOrderByIdAsc(1000L)).thenReturn(List.of(orderItem));

        OrderResponse response = orderService.getMyOrder(1L, 1000L);

        assertEquals(1000L, response.id());
        assertEquals(1, response.items().size());
        assertEquals(100L, response.items().getFirst().dropId());
    }

    @Test
    @DisplayName("본인 주문이 아니면 주문 상세를 조회할 수 없다")
    void rejectAnotherUsersOrder() {
        when(orderRepository.findByIdAndUser_Id(1000L, 2L)).thenReturn(Optional.empty());

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> orderService.getMyOrder(2L, 1000L)
        );

        assertEquals(OrderErrorCode.ORDER_NOT_FOUND, exception.getErrorCode());
        verify(orderItemRepository, never()).findAllByOrder_IdOrderByIdAsc(any());
    }

    @Test
    @DisplayName("주문을 취소하면 주문 상태를 변경하고 재고를 복구한다")
    void cancelOrder() {
        User buyer = createUser(1L, UserRole.USER);
        Drop drop = createOpenDrop(10L, 100L, 10, 20, 2);
        drop.decreaseStock(2, LocalDateTime.now());
        Order order = new Order(buyer, new BigDecimal("94400"));
        ReflectionTestUtils.setField(order, "id", 1000L);
        OrderItem orderItem = new OrderItem(order, drop, "Limited Hoodie", new BigDecimal("59000"), 20, 2);

        when(orderRepository.findByIdAndUser_Id(1000L, 1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_IdOrderByIdAsc(1000L)).thenReturn(List.of(orderItem));

        orderService.cancel(1L, 1000L);

        assertEquals(OrderStatus.CANCELED, order.getStatus());
        assertEquals(10, drop.getRemainingQuantity());
    }

    private User createUser(Long id, UserRole role) {
        User user = new User("user@example.com", "password", "user", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Drop createOpenDrop(Long sellerId, Long dropId, int stock, int discountRate, int purchaseLimit) {
        User seller = createUser(sellerId, UserRole.SELLER);
        Product product = new Product(seller, "Limited Hoodie", "description", null);
        ReflectionTestUtils.setField(product, "id", 10L);
        Drop drop = new Drop(
                product,
                new BigDecimal("59000"),
                stock,
                discountRate,
                purchaseLimit,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1)
        );
        ReflectionTestUtils.setField(drop, "id", dropId);
        drop.changeVisibility(true);
        return drop;
    }
}
