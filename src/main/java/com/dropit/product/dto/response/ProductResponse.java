package com.dropit.product.dto.response;

import com.dropit.product.entity.Product;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ProductResponse {

    private final Long id;
    private final Long sellerId;
    private final String sellerName;

    private final String name;
    private final String description;
    private final String imageUrl;

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public ProductResponse(Product product) {
        this.id = product.getId();
        this.sellerId = product.getSeller().getId();
        this.sellerName = product.getSeller().getUsername();

        this.name = product.getName();
        this.description = product.getDescription();
        this.imageUrl = product.getImageUrl();

        this.createdAt = product.getCreatedAt();
        this.updatedAt = product.getUpdatedAt();
    }
}
