package com.dropit.order.entity;

import com.dropit.drop.entity.Drop;
import com.dropit.product.entity.Product;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderItemTest {

    @Test
    @DisplayName("주문 항목은 정가와 할인율을 스냅샷으로 저장하고 할인 적용 금액을 계산한다")
    void createOrderItemWithPriceSnapshotsAndDiscountedTotal() {
        User buyer = new User("buyer@example.com", "encoded-password", "buyer", UserRole.USER);
        User seller = new User("seller@example.com", "encoded-password", "seller", UserRole.SELLER);
        Product product = new Product(seller, "Limited Hoodie", "description", null);
        Drop drop = new Drop(
                product,
                new BigDecimal("59000"),
                10,
                20,
                0,
                LocalDateTime.of(2026, 9, 3, 12, 0),
                LocalDateTime.of(2026, 9, 3, 13, 0)
        );
        Order order = new Order(buyer, new BigDecimal("94400"));

        OrderItem orderItem = new OrderItem(
                order,
                drop,
                product.getName(),
                drop.getPrice(),
                drop.getDiscountRate(),
                2
        );

        assertAll(
                () -> assertEquals(order, orderItem.getOrder()),
                () -> assertEquals(drop, orderItem.getDrop()),
                () -> assertEquals("Limited Hoodie", orderItem.getProductName()),
                () -> assertEquals(new BigDecimal("59000"), orderItem.getUnitPrice()),
                () -> assertEquals(20, orderItem.getDiscountRate()),
                () -> assertEquals(2, orderItem.getQuantity()),
                () -> assertEquals(new BigDecimal("94400"), orderItem.getItemTotalPrice())
        );
    }

    @Test
    @DisplayName("할인율이 0이면 정가와 수량으로 주문 항목 금액을 계산한다")
    void calculateItemTotalPriceWithoutDiscount() {
        OrderItem orderItem = createOrderItem(new BigDecimal("59000"), 0, 2);

        assertEquals(new BigDecimal("118000"), orderItem.getItemTotalPrice());
    }

    @Test
    @DisplayName("할인 적용 금액에 원 단위 미만이 생기면 버림 처리한다")
    void roundDownFractionalWonInItemTotalPrice() {
        OrderItem orderItem = createOrderItem(new BigDecimal("9999"), 10, 1);

        assertEquals(new BigDecimal("8999"), orderItem.getItemTotalPrice());
    }

    private OrderItem createOrderItem(BigDecimal price, int discountRate, int quantity) {
        User buyer = new User("buyer@example.com", "encoded-password", "buyer", UserRole.USER);
        User seller = new User("seller@example.com", "encoded-password", "seller", UserRole.SELLER);
        Product product = new Product(seller, "Limited Hoodie", "description", null);
        Drop drop = new Drop(
                product,
                price,
                10,
                discountRate,
                0,
                LocalDateTime.of(2026, 9, 3, 12, 0),
                LocalDateTime.of(2026, 9, 3, 13, 0)
        );
        Order order = new Order(buyer, BigDecimal.ZERO);

        return new OrderItem(
                order,
                drop,
                product.getName(),
                drop.getPrice(),
                drop.getDiscountRate(),
                quantity
        );
    }
}
