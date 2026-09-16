package com.dropit.ranking.repository;

public record SellerSales(long sellerId, long totalQuantity, long monthlyQuantity) {

    private static final long MAX_EXACT_SCORE = 9_007_199_254_740_991L;

    public SellerSales {
        if (sellerId <= 0 || totalQuantity <= 0 || totalQuantity > MAX_EXACT_SCORE
                || monthlyQuantity < 0 || monthlyQuantity > totalQuantity) {
            throw new IllegalArgumentException("유효하지 않은 판매자 집계 결과입니다.");
        }
    }
}
