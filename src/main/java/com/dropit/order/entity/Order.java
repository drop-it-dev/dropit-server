package com.dropit.order.entity;

import com.dropit.global.entity.BaseEntity;
import com.dropit.global.exception.ServiceException;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 모든 주문 항목의 금액을 합한 전체 결제 금액
    @Column(name = "total_price", nullable = false, precision = 13, scale = 0)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    public Order(User user, BigDecimal totalPrice) {
        this.user = user;
        this.totalPrice = totalPrice;
        this.status = OrderStatus.ORDERED;
    }

    public void cancel() {
        if (status == OrderStatus.CANCELED) {
            throw new ServiceException(OrderErrorCode.ORDER_ALREADY_CANCELED);
        }

        this.status = OrderStatus.CANCELED;
    }
}
