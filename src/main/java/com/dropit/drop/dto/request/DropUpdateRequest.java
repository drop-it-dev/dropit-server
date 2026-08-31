package com.dropit.drop.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;

public record DropUpdateRequest(
        @Positive(message = "초기 수량은 1개 이상이어야 합니다.")
        Integer initialQuantity,

        @Min(value = 0, message = "할인율은 0 이상이어야 합니다.")
        @Max(value = 99, message = "할인율은 99 이하여야 합니다.")
        Integer discountRate,

        @PositiveOrZero(message = "구매 제한 수량은 0개 이상이어야 합니다.")
        Integer purchaseLimit,

        LocalDateTime openAt,

        LocalDateTime closeAt
) {
}
