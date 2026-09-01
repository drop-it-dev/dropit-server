package com.dropit.drop.entity;

import com.dropit.drop.exception.DropErrorCode;
import com.dropit.global.entity.BaseEntity;
import com.dropit.global.exception.ServiceException;
import com.dropit.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "initial_quantity", nullable = false)
    private int initialQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @Column(name = "discount_rate", nullable = false)
    private int discountRate; // 0이면 할인 없음

    @Column(name = "purchase_limit", nullable = false)
    private int purchaseLimit; // 0이면 1인당 구매 제한 없음

    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    @Column(name = "close_at", nullable = false)
    private LocalDateTime closeAt;

    public Drop(
            Product product,
            BigDecimal price,
            int initialQuantity,
            int discountRate,
            int purchaseLimit,
            LocalDateTime openAt,
            LocalDateTime closeAt
    ) {
        validatePeriod(openAt, closeAt);

        this.product = product;
        this.price = price;
        this.initialQuantity = initialQuantity;
        this.remainingQuantity = initialQuantity;
        this.discountRate = discountRate;
        this.purchaseLimit = purchaseLimit;
        this.openAt = openAt;
        this.closeAt = closeAt;
    }

    public DropStatus currentStatus(LocalDateTime now) {
        if (!now.isBefore(closeAt))  return DropStatus.CLOSED;
        if (now.isBefore(openAt)) return DropStatus.READY;
        if (remainingQuantity == 0) return DropStatus.SOLDOUT;

        // openAt <= now < closeAt
        return DropStatus.OPEN;
    }

    public void update(
            BigDecimal price,
            Integer initialQuantity,
            Integer discountRate,
            Integer purchaseLimit,
            LocalDateTime openAt,
            LocalDateTime closeAt
    ) {
        int updatedInitialQuantity = initialQuantity != null ? initialQuantity : this.initialQuantity;
        LocalDateTime updatedOpenAt = openAt != null ? openAt : this.openAt;
        LocalDateTime updatedCloseAt = closeAt != null ? closeAt : this.closeAt;
        validatePeriod(updatedOpenAt, updatedCloseAt);

        if (this.remainingQuantity == this.initialQuantity) {
            this.remainingQuantity = updatedInitialQuantity;
        }
        this.initialQuantity = updatedInitialQuantity;
        this.price = price != null ? price : this.price;
        this.discountRate = discountRate != null ? discountRate : this.discountRate;
        this.purchaseLimit = purchaseLimit != null ? purchaseLimit : this.purchaseLimit;
        this.openAt = updatedOpenAt;
        this.closeAt = updatedCloseAt;
    }

    private static void validatePeriod(LocalDateTime openAt, LocalDateTime closeAt) {
        if (!openAt.isBefore(closeAt)) {
            throw new ServiceException(DropErrorCode.INVALID_DROP_PERIOD);
        }
    }

    public void ensureEditable(LocalDateTime now) {
        if (!now.isBefore(openAt)) {
            throw new ServiceException(DropErrorCode.DROP_ALREADY_STARTED);
        }
    }
}
