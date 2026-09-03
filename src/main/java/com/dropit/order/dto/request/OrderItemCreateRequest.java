package com.dropit.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemCreateRequest(
        @NotNull(message = "드랍 ID는 필수입니다.")
        Long dropId,

        @Positive(message = "주문 수량은 1개 이상이어야 합니다.")
        int quantity
) {
}
