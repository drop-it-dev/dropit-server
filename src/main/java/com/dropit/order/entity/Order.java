package com.dropit.order.entity;

import com.dropit.drop.entity.Drop;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drop_id", nullable = false)
    private Drop drop;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "order_quantity", nullable = false)
    private int orderQuantity;

    public Order(User user, Drop drop, BigDecimal price, int orderQuantity) {
        this.user = user;
        this.drop = drop;
        this.price = price;
        this.orderQuantity = orderQuantity;
    }
}
