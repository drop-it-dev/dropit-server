package com.dropit.drop.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;

public record DropCreateRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,

        @Positive(message = "초기 수량은 1개 이상이어야 합니다.")
        int initialQuantity,

        @Min(value = 0, message = "할인율은 0 이상이어야 합니다.")
        @Max(value = 99, message = "할인율은 99 이하여야 합니다.")
        int discountRate,

        @NotNull(message = "구매 제한 수량은 필수입니다.")
        @PositiveOrZero(message = "구매 제한 수량은 0개 이상이어야 합니다.")
        Integer purchaseLimit,

        @NotNull(message = "판매 시작 시각은 필수입니다.")
        LocalDateTime openAt,

        @NotNull(message = "판매 종료 시각은 필수입니다.")
        LocalDateTime closeAt
) {
}
