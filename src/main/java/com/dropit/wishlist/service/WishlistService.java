package com.dropit.wishlist.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.product.entity.Product;
import com.dropit.product.repository.ProductRepository;
import com.dropit.user.entity.User;
import com.dropit.user.repository.UserRepository;
import com.dropit.wishlist.dto.response.WishlistResponse;
import com.dropit.wishlist.entity.Wishlist;
import com.dropit.wishlist.exception.WishlistErrorCode;
import com.dropit.wishlist.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional
    public WishlistResponse add(Long userId, Long productId) {
        User user = findUser(userId);
        Product product = findProduct(productId);

        if (wishlistRepository.existsByUser_IdAndProduct_Id(userId, productId)) {
            throw new ServiceException(
                    WishlistErrorCode.WISHLIST_ALREADY_EXISTS
            );
        }

        Wishlist wishlist = new Wishlist(user, product);
        Wishlist savedWishlist = wishlistRepository.save(wishlist);

        return new WishlistResponse(savedWishlist);
    }

    public List<WishlistResponse> getMine(Long userId) {
        findUser(userId);

        return wishlistRepository.findAllByUser_Id(userId)
                .stream()
                .map(WishlistResponse::new)
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long productId) {
        Wishlist wishlist =
                wishlistRepository.findByUser_IdAndProduct_Id(userId, productId)
                        .orElseThrow(() ->
                                new ServiceException(
                                        WishlistErrorCode.WISHLIST_NOT_FOUND
                                )
                        );

        wishlistRepository.delete(wishlist);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ServiceException(
                                WishlistErrorCode.USER_NOT_FOUND
                        )
                );
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() ->
                        new ServiceException(
                                WishlistErrorCode.PRODUCT_NOT_FOUND
                        )
                );
    }
}