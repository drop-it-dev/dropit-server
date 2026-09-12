package com.dropit.order.entity;

import com.dropit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "order_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_requests_user_drop_key_hash",
                columnNames = {"user_id", "drop_id", "idempotency_key_hash"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderRequest extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderRequestStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    private Order order;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "drop_id", nullable = false)
    private Long dropId;

    @Column(name = "idempotency_key_hash", nullable = false, length = 64)
    private String idempotencyKeyHash;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 13, scale = 0)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "discount_rate", nullable = false)
    private int discountRate;

    public OrderRequest(
            UUID requestId,
            Long userId,
            Long dropId,
            String idempotencyKeyHash,
            String payloadHash,
            String productName,
            BigDecimal unitPrice,
            int quantity,
            int discountRate
    ) {
        this.id = requestId;
        this.status = OrderRequestStatus.PENDING;
        this.userId = userId;
        this.dropId = dropId;
        this.idempotencyKeyHash = idempotencyKeyHash;
        this.payloadHash = payloadHash;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.discountRate = discountRate;
    }
}
