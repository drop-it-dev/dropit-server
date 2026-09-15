package com.dropit.wishlist.controller;

import com.dropit.global.security.principal.CurrentUserId;
import com.dropit.wishlist.dto.response.WishlistResponse;
import com.dropit.wishlist.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wishlists")
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping("/{productId}")
    public ResponseEntity<WishlistResponse> add(
            @CurrentUserId Long userId,
            @PathVariable Long productId
    ) {
        WishlistResponse response =
                wishlistService.add(userId, productId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<WishlistResponse>> getMine(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(
                wishlistService.getMine(userId)
        );
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> delete(
            @CurrentUserId Long userId,
            @PathVariable Long productId
    ) {
        wishlistService.delete(userId, productId);

        return ResponseEntity.noContent().build();
    }
}