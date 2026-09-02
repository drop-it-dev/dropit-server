package com.dropit.order.entity;

import com.dropit.global.entity.BaseEntity;
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

    public Order(User user, BigDecimal totalPrice) {
        this.user = user;
        this.totalPrice = totalPrice;
    }
}
