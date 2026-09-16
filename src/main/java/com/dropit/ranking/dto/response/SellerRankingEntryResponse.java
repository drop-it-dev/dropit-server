package com.dropit.ranking.dto.response;

public record SellerRankingEntryResponse(
        long rank,
        long sellerId,
        long salesQuantity
) {
}
