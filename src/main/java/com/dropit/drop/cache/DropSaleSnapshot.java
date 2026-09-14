package com.dropit.drop.cache;

import com.dropit.drop.entity.Drop;

import java.math.BigDecimal;
import java.time.ZoneId;

public record DropSaleSnapshot(
        Long dropId,
        boolean visible,
        long openAtEpochMillis,
        long closeAtEpochMillis,
        BigDecimal unitPrice,
        int discountRate,
        int purchaseLimit,
        String productName,
        long saleVersion,
        long operationVersion,
        int remainingQuantity
) {
    private static final ZoneId SALE_ZONE = ZoneId.of("Asia/Seoul");

    public static DropSaleSnapshot from(Drop drop) {
        return new DropSaleSnapshot(
                drop.getId(), drop.isVisible(),
                drop.getOpenAt().atZone(SALE_ZONE).toInstant().toEpochMilli(),
                drop.getCloseAt().atZone(SALE_ZONE).toInstant().toEpochMilli(),
                drop.getPrice(), drop.getDiscountRate(), drop.getPurchaseLimit(),
                drop.getProduct().getName(), drop.getSaleVersion(),
                drop.getAdmissionOperationVersion(), drop.getRemainingQuantity()
        );
    }

    public long retentionUntilEpochMillis() {
        return closeAtEpochMillis + 86_400_000L;
    }
}
