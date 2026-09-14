package com.dropit.drop.cache;

import com.dropit.drop.entity.Drop;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Drop 상세 조회에서 반복 사용되는 메타데이터 캐시 값입니다.
 * 주문마다 변경되는 재고와 재고 기반 상태는 포함하지 않습니다.
 */
public record DropDetailCacheValue(
        Long id,
        Long sellerId,
        String sellerName,
        Long productId,
        String productName,
        String imageUrl,
        BigDecimal price,
        int discountRate,
        int initialQuantity,
        int purchaseLimit,
        boolean visible,
        LocalDateTime openAt,
        LocalDateTime closeAt
) {
    public static DropDetailCacheValue from(Drop drop) {
        return new DropDetailCacheValue(
                drop.getId(),
                drop.getProduct().getSeller().getId(),
                drop.getProduct().getSeller().getUsername(),
                drop.getProduct().getId(),
                drop.getProduct().getName(),
                drop.getProduct().getImageUrl(),
                drop.getPrice(),
                drop.getDiscountRate(),
                drop.getInitialQuantity(),
                drop.getPurchaseLimit(),
                drop.isVisible(),
                drop.getOpenAt(),
                drop.getCloseAt()
        );
    }
}
