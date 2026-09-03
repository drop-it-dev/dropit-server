package com.dropit.order.entity;

import com.dropit.global.exception.ServiceException;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class OrderTest {

    private final User user = mock(User.class);

    @Test
    @DisplayName("주문은 주문 완료 상태로 생성된다")
    void createOrderAsOrdered() {
        Order order = new Order(user, new BigDecimal("94400"));

        assertEquals(OrderStatus.ORDERED, order.getStatus());
        assertEquals(new BigDecimal("94400"), order.getTotalPrice());
    }

    @Test
    @DisplayName("주문을 취소 상태로 변경할 수 있다")
    void cancelOrder() {
        Order order = new Order(user, new BigDecimal("94400"));

        order.cancel();

        assertEquals(OrderStatus.CANCELED, order.getStatus());
    }

    @Test
    @DisplayName("이미 취소한 주문은 다시 취소할 수 없다")
    void rejectAlreadyCanceledOrder() {
        Order order = new Order(user, new BigDecimal("94400"));
        order.cancel();

        ServiceException exception = assertThrows(ServiceException.class, order::cancel);

        assertEquals(OrderErrorCode.ORDER_ALREADY_CANCELED, exception.getErrorCode());
    }
}
