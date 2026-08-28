package com.dropit.drop.entity;

import com.dropit.global.entity.BaseEntity;
import com.dropit.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "drops")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Drop extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "initial_quantity", nullable = false)
    private int initialQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DropStatus status = DropStatus.READY;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    @Column(name = "close_at", nullable = false)
    private LocalDateTime closeAt;

    @Column(name = "purchase_limit", nullable = false)
    private int purchaseLimit;

    public Drop(
            Product product,
            int initialQuantity,
            int discountAmount,
            LocalDateTime openAt,
            LocalDateTime closeAt,
            int purchaseLimit
    ) {
        this.product = product;
        this.initialQuantity = initialQuantity;
        this.remainingQuantity = initialQuantity;
        this.discountAmount = discountAmount;
        this.openAt = openAt;
        this.closeAt = closeAt;
        this.purchaseLimit = purchaseLimit;
    }
}
