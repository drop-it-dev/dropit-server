package com.dropit.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProductUpdateRequest {

    @NotBlank(message = "상품명은 필수입니다.")
    @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
    private String name;

    @Size(max = 3000, message = "상품 설명은 3000자 이하여야 합니다.")
    private String description;

    public ProductUpdateRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
