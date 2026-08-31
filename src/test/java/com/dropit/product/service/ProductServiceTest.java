package com.dropit.product.service;

import com.dropit.product.dto.request.ProductCreateRequest;
import com.dropit.product.dto.response.ProductResponse;
import com.dropit.product.entity.Product;
import com.dropit.product.repository.ProductRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("판매자는 상품을 등록할 수 있다")
    void createProduct() {
        Long sellerId = 1L;
        User seller = createUser(UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", sellerId);
        ProductCreateRequest request = new ProductCreateRequest(
                "Limited Hoodie",
                new BigDecimal("59000.00"),
                "Limited edition hoodie"
        );

        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 100L);
            ReflectionTestUtils.setField(product, "createdAt", LocalDateTime.of(2026, 8, 31, 10, 0));
            ReflectionTestUtils.setField(product, "updatedAt", LocalDateTime.of(2026, 8, 31, 10, 0));
            return product;
        });

        ProductResponse response = productService.createProduct(sellerId, request);

        assertEquals(100L, response.getId());
        assertEquals(sellerId, response.getSellerId());
        assertEquals("username", response.getSellerName());
        assertEquals("Limited Hoodie", response.getName());
        assertEquals(new BigDecimal("59000.00"), response.getPrice());
        assertEquals("Limited edition hoodie", response.getDescription());
        assertNull(response.getImageUrl());
        assertEquals(LocalDateTime.of(2026, 8, 31, 10, 0), response.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 8, 31, 10, 0), response.getUpdatedAt());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());

        Product savedProduct = productCaptor.getValue();
        assertEquals(seller, savedProduct.getSeller());
        assertEquals("Limited Hoodie", savedProduct.getName());
        assertEquals(new BigDecimal("59000.00"), savedProduct.getPrice());
        assertEquals("Limited edition hoodie", savedProduct.getDescription());
        assertNull(savedProduct.getImageUrl());
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 상품을 등록할 수 없다")
    void rejectMissingUser() {
        Long sellerId = 1L;
        ProductCreateRequest request = createRequest();
        when(userRepository.findById(sellerId)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> productService.createProduct(sellerId, request)
        );

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("일반 사용자는 상품을 등록할 수 없다")
    void rejectNonSeller() {
        Long userId = 1L;
        User user = createUser(UserRole.USER);
        ProductCreateRequest request = createRequest();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(
                IllegalStateException.class,
                () -> productService.createProduct(userId, request)
        );

        verify(productRepository, never()).save(any(Product.class));
    }

    private ProductCreateRequest createRequest() {
        return new ProductCreateRequest(
                "Product",
                new BigDecimal("10000.00"),
                "Description"
        );
    }

    private User createUser(UserRole role) {
        return new User(
                "user@example.com",
                "encoded-password",
                "username",
                role
        );
    }
}
