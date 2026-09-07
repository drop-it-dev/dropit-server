package com.dropit.drop.dto.request;

import jakarta.validation.constraints.NotNull;

public record DropVisibilityUpdateRequest(
        @NotNull(message = "공개 여부는 필수입니다.")
        Boolean visible
) {
}
