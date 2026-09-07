package com.dropit.order.entity;

import com.dropit.drop.entity.Drop;
import com.dropit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Entity
@Table(name = "order_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // 어떤 드랍 판매에서 구매했는지 추적하기 위한 원본 참조
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drop_id", nullable = false)
    private Drop drop;

    // 주문 당시 상품명 스냅샷
    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    // 주문 당시 정가 스냅샷
    @Column(name = "unit_price", nullable = false, precision = 13, scale = 0)
    private BigDecimal unitPrice;

    // 주문 당시 적용된 할인율 스냅샷
    @Column(name = "discount_rate", nullable = false)
    private int discountRate;

    // 같은 상품+옵션 조합으로 구매한 수량
    @Column(nullable = false)
    private int quantity;

    // 이 주문 항목의 최종 결제 금액: unitPrice × (1 - discountRate × 0.01) × quantity
    @Column(name = "item_total_price", nullable = false, precision = 13, scale = 0)
    private BigDecimal itemTotalPrice;

    public OrderItem(
            Order order,
            Drop drop,
            String productName,
            BigDecimal unitPrice,
            int discountRate,
            int quantity
    ) {
        this.order = order;
        this.drop = drop;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.discountRate = discountRate;
        this.quantity = quantity;
        this.itemTotalPrice = calculateItemTotalPrice(unitPrice, discountRate, quantity);
    }

    public static BigDecimal calculateItemTotalPrice(
            BigDecimal unitPrice,
            int discountRate,
            int quantity
    ) {
        return unitPrice
                .multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(discountRate).movePointLeft(2)))
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(0, RoundingMode.DOWN);
    }
}
