package com.dropit.drop.cache;

import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.entity.DropStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DropListCacheItem(
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

    public static DropListCacheItem from(Drop drop) {
        return new DropListCacheItem(
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

    public DropResponse toResponse(int remainingQuantity, LocalDateTime now) {
        return new DropResponse(
                id,
                sellerId,
                sellerName,
                productId,
                productName,
                imageUrl,
                price,
                discountRate,
                initialQuantity,
                remainingQuantity,
                initialQuantity - remainingQuantity,
                purchaseLimit,
                visible,
                openAt,
                closeAt,
                currentStatus(remainingQuantity, now)
        );
    }

    private DropStatus currentStatus(int remainingQuantity, LocalDateTime now) {
        if (!now.isBefore(closeAt)) {
            return DropStatus.CLOSED;
        }
        if (now.isBefore(openAt)) {
            return DropStatus.READY;
        }
        if (remainingQuantity == 0) {
            return DropStatus.SOLDOUT;
        }
        return DropStatus.OPEN;
    }
}
