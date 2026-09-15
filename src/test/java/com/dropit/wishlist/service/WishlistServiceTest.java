package com.dropit.wishlist.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.product.entity.Product;
import com.dropit.product.repository.ProductRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import com.dropit.wishlist.dto.response.WishlistResponse;
import com.dropit.wishlist.entity.Wishlist;
import com.dropit.wishlist.exception.WishlistErrorCode;
import com.dropit.wishlist.repository.WishlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private WishlistService wishlistService;

    @Test
    @DisplayName("상품을 찜 목록에 추가한다")
    void addWishlist() {
        User user = createUser(1L);
        Product product = createProduct(10L);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));
        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));
        when(wishlistRepository.existsByUser_IdAndProduct_Id(1L, 10L))
                .thenReturn(false);
        when(wishlistRepository.save(any(Wishlist.class)))
                .thenAnswer(invocation -> {
                    Wishlist wishlist = invocation.getArgument(0);
                    ReflectionTestUtils.setField(wishlist, "id", 100L);
                    return wishlist;
                });

        WishlistResponse response = wishlistService.add(1L, 10L);

        assertEquals(100L, response.getId());
        assertEquals(10L, response.getProductId());

        verify(wishlistRepository).save(any(Wishlist.class));
    }

    @Test
    @DisplayName("이미 찜한 상품은 다시 추가할 수 없다")
    void rejectDuplicateWishlist() {
        User user = createUser(1L);
        Product product = createProduct(10L);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));
        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));
        when(wishlistRepository.existsByUser_IdAndProduct_Id(1L, 10L))
                .thenReturn(true);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> wishlistService.add(1L, 10L)
        );

        assertEquals(
                WishlistErrorCode.WISHLIST_ALREADY_EXISTS,
                exception.getErrorCode()
        );

        verify(wishlistRepository, never())
                .save(any(Wishlist.class));
    }

    @Test
    @DisplayName("내 찜 목록을 조회한다")
    void getMine() {
        User user = createUser(1L);
        Product product = createProduct(10L);

        Wishlist wishlist = new Wishlist(user, product);
        ReflectionTestUtils.setField(wishlist, "id", 100L);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));
        when(wishlistRepository.findAllByUser_Id(1L))
                .thenReturn(List.of(wishlist));

        List<WishlistResponse> responses = wishlistService.getMine(1L);

        assertEquals(1, responses.size());
        assertEquals(100L, responses.getFirst().getId());
        assertEquals(10L, responses.getFirst().getProductId());
    }

    @Test
    @DisplayName("찜한 상품을 삭제한다")
    void deleteWishlist() {
        User user = createUser(1L);
        Product product = createProduct(10L);
        Wishlist wishlist = new Wishlist(user, product);

        when(wishlistRepository.findByUser_IdAndProduct_Id(1L, 10L))
                .thenReturn(Optional.of(wishlist));

        wishlistService.delete(1L, 10L);

        verify(wishlistRepository).delete(wishlist);
    }

    @Test
    @DisplayName("존재하지 않는 찜은 삭제할 수 없다")
    void rejectDeleteMissingWishlist() {
        when(wishlistRepository.findByUser_IdAndProduct_Id(1L, 10L))
                .thenReturn(Optional.empty());

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> wishlistService.delete(1L, 10L)
        );

        assertEquals(
                WishlistErrorCode.WISHLIST_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(wishlistRepository, never())
                .delete(any(Wishlist.class));
    }

    private User createUser(Long id) {
        User user = new User(
                "user@example.com",
                "password",
                "user",
                UserRole.USER
        );

        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Product createProduct(Long id) {
        User seller = new User(
                "seller@example.com",
                "password",
                "seller",
                UserRole.SELLER
        );

        Product product = new Product(
                seller,
                "Limited Product",
                "description",
                "image.jpg"
        );

        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
