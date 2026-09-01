package com.dropit.drop.dto.response;

import com.dropit.drop.entity.Drop;
import com.dropit.drop.entity.DropStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DropResponse(
        Long id,
        Long sellerId,
        String sellerName,

        Long productId,
        String productName,
        String imageUrl,

        BigDecimal price,
        int discountRate,

        int initialQuantity,
        int remainingQuantity,
        int soldQuantity,

        int purchaseLimit,

        LocalDateTime openAt,
        LocalDateTime closeAt,

        DropStatus status
) {
    public static DropResponse from(Drop drop) {
        return new DropResponse(
                drop.getId(),
                drop.getProduct().getSeller().getId(),
                drop.getProduct().getSeller().getUsername(),

                drop.getProduct().getId(),
                drop.getProduct().getName(),
                drop.getProduct().getImageUrl(),

                drop.getPrice(),
                drop.getDiscountRate(),

                drop.getInitialQuantity(),
                drop.getRemainingQuantity(),
                drop.getInitialQuantity() - drop.getRemainingQuantity(),

                drop.getPurchaseLimit(),

                drop.getOpenAt(),
                drop.getCloseAt(),

                drop.currentStatus(LocalDateTime.now())
        );
    }
}
