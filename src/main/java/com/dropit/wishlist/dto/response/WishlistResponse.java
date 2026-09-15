package com.dropit.wishlist.dto.response;

import com.dropit.wishlist.entity.Wishlist;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class WishlistResponse {

    private final Long id;
    private final Long productId;
    private final String productName;
    private final String productImageUrl;
    private final LocalDateTime createdAt;

    public WishlistResponse(Wishlist wishlist) {
        this.id = wishlist.getId();
        this.productId = wishlist.getProduct().getId();
        this.productName = wishlist.getProduct().getName();
        this.productImageUrl = wishlist.getProduct().getImageUrl();
        this.createdAt = wishlist.getCreatedAt();
    }
}
