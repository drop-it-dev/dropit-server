package com.dropit.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProductCreateRequest {

    @NotBlank(message = "상품명은 필수입니다.")
    @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
    private String name;

    private String description;

    public ProductCreateRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
