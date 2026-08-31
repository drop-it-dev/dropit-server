package com.dropit.product.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.product.dto.request.ProductCreateRequest;
import com.dropit.product.dto.request.ProductUpdateRequest;
import com.dropit.product.dto.response.ProductResponse;
import com.dropit.product.entity.Product;
import com.dropit.product.exception.ProductErrorCode;
import com.dropit.product.repository.ProductRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
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
        ProductCreateRequest request = new ProductCreateRequest(
                "Limited Hoodie",
                new BigDecimal("59000.00"),
                "Limited edition hoodie"
        );

        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 100L);
            return product;
        });

        Long productId = productService.create(sellerId, request);

        assertEquals(100L, productId);

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

        assertServiceException(
                ProductErrorCode.SELLER_NOT_FOUND,
                () -> productService.create(sellerId, request)
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

        assertServiceException(
                ProductErrorCode.SELLER_ROLE_REQUIRED,
                () -> productService.create(userId, request)
        );

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("상품 ID로 상품 상세 정보를 조회한다")
    void getProduct() {
        Long productId = 100L;
        User seller = createUser(UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", 1L);

        Product product = new Product(
                seller,
                "Limited Hoodie",
                new BigDecimal("59000.00"),
                "Limited edition hoodie",
                "products/limited-hoodie.webp"
        );
        ReflectionTestUtils.setField(product, "id", productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        ProductResponse response = productService.getProduct(productId);

        assertEquals(productId, response.getId());
        assertEquals(1L, response.getSellerId());
        assertEquals("username", response.getSellerName());
        assertEquals("Limited Hoodie", response.getName());
        assertEquals(new BigDecimal("59000.00"), response.getPrice());
        assertEquals("Limited edition hoodie", response.getDescription());
        assertEquals("products/limited-hoodie.webp", response.getImageUrl());
    }

    @Test
    @DisplayName("존재하지 않는 상품은 조회할 수 없다")
    void rejectMissingProduct() {
        Long productId = 999L;
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertServiceException(
                ProductErrorCode.PRODUCT_NOT_FOUND,
                () -> productService.getProduct(productId)
        );
    }

    @Test
    @DisplayName("전체 상품 목록을 조회한다")
    void getProducts() {
        User seller = createUser(UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", 1L);

        Product firstProduct = new Product(
                seller,
                "First Product",
                new BigDecimal("10000.00"),
                "First description",
                null
        );
        Product secondProduct = new Product(
                seller,
                "Second Product",
                new BigDecimal("20000.00"),
                "Second description",
                null
        );
        ReflectionTestUtils.setField(firstProduct, "id", 1L);
        ReflectionTestUtils.setField(secondProduct, "id", 2L);

        when(productRepository.findAll()).thenReturn(List.of(firstProduct, secondProduct));

        List<ProductResponse> response = productService.getProducts();

        assertEquals(2, response.size());
        assertEquals("First Product", response.get(0).getName());
        assertEquals("Second Product", response.get(1).getName());
        verify(productRepository).findAll();
    }

    @Test
    @DisplayName("상품 소유자는 상품 정보를 수정할 수 있다")
    void updateProduct() {
        Long sellerId = 1L;
        Long productId = 100L;
        User seller = createUser(UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Product product = new Product(
                seller,
                "Original Product",
                new BigDecimal("30000.00"),
                "Original description",
                null
        );
        ReflectionTestUtils.setField(product, "id", productId);

        ProductUpdateRequest request = new ProductUpdateRequest(
                "Updated Product",
                new BigDecimal("49000.00"),
                "Updated description"
        );
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        ProductResponse response = productService.update(sellerId, productId, request);

        assertEquals("Updated Product", product.getName());
        assertEquals(new BigDecimal("49000.00"), product.getPrice());
        assertEquals("Updated description", product.getDescription());
        assertEquals("Updated Product", response.getName());
        assertEquals(new BigDecimal("49000.00"), response.getPrice());
        verify(productRepository).findById(productId);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("존재하지 않는 상품은 수정할 수 없다")
    void rejectUpdatingMissingProduct() {
        Long productId = 999L;
        ProductUpdateRequest request = new ProductUpdateRequest(
                "Updated Product",
                new BigDecimal("49000.00"),
                null
        );
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertServiceException(
                ProductErrorCode.PRODUCT_NOT_FOUND,
                () -> productService.update(1L, productId, request)
        );
    }

    @Test
    @DisplayName("상품 소유자가 아니면 상품을 수정할 수 없다")
    void rejectUpdatingAnotherSellersProduct() {
        Long ownerId = 1L;
        Long otherSellerId = 2L;
        Long productId = 100L;
        User owner = createUser(UserRole.SELLER);
        ReflectionTestUtils.setField(owner, "id", ownerId);

        Product product = new Product(
                owner,
                "Original Product",
                new BigDecimal("30000.00"),
                "Original description",
                null
        );
        ReflectionTestUtils.setField(product, "id", productId);

        ProductUpdateRequest request = new ProductUpdateRequest(
                "Updated Product",
                new BigDecimal("49000.00"),
                "Updated description"
        );
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertServiceException(
                ProductErrorCode.PRODUCT_OWNER_REQUIRED,
                () -> productService.update(otherSellerId, productId, request)
        );

        assertEquals("Original Product", product.getName());
        assertEquals(new BigDecimal("30000.00"), product.getPrice());
        assertEquals("Original description", product.getDescription());
    }

    @Test
    @DisplayName("상품 소유자는 상품을 삭제할 수 있다")
    void deleteProduct() {
        Long sellerId = 1L;
        Long productId = 100L;
        User seller = createUser(UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Product product = new Product(
                seller,
                "Product",
                new BigDecimal("30000.00"),
                null,
                null
        );
        ReflectionTestUtils.setField(product, "id", productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        productService.delete(sellerId, productId);

        verify(productRepository).delete(product);
    }

    @Test
    @DisplayName("존재하지 않는 상품은 삭제할 수 없다")
    void rejectDeletingMissingProduct() {
        Long productId = 999L;
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertServiceException(
                ProductErrorCode.PRODUCT_NOT_FOUND,
                () -> productService.delete(1L, productId)
        );

        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    @DisplayName("상품 소유자가 아니면 상품을 삭제할 수 없다")
    void rejectDeletingAnotherSellersProduct() {
        Long ownerId = 1L;
        Long otherSellerId = 2L;
        Long productId = 100L;
        User owner = createUser(UserRole.SELLER);
        ReflectionTestUtils.setField(owner, "id", ownerId);

        Product product = new Product(
                owner,
                "Product",
                new BigDecimal("30000.00"),
                null,
                null
        );
        ReflectionTestUtils.setField(product, "id", productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertServiceException(
                ProductErrorCode.PRODUCT_OWNER_REQUIRED,
                () -> productService.delete(otherSellerId, productId)
        );

        verify(productRepository, never()).delete(any(Product.class));
    }

    private ProductCreateRequest createRequest() {
        return new ProductCreateRequest(
                "Product",
                new BigDecimal("10000.00"),
                "Description"
        );
    }

    private void assertServiceException(ProductErrorCode errorCode, Executable executable) {
        ServiceException exception = assertThrows(ServiceException.class, executable);

        assertEquals(errorCode, exception.getErrorCode());
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
