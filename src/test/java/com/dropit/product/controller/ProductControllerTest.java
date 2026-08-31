package com.dropit.product.controller;

import com.dropit.global.exception.GlobalExceptionHandler;
import com.dropit.global.exception.ServiceException;
import com.dropit.product.dto.request.ProductCreateRequest;
import com.dropit.product.dto.request.ProductUpdateRequest;
import com.dropit.product.dto.response.ProductResponse;
import com.dropit.product.entity.Product;
import com.dropit.product.exception.ProductErrorCode;
import com.dropit.product.service.ProductService;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerTest {

    private MockMvc mockMvc;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        ProductController productController = new ProductController(productService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(productController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("상품을 등록하면 201 상태와 생성된 상품 ID를 반환한다")
    void create() throws Exception {
        Long sellerId = 1L;
        when(productService.create(eq(sellerId), any(ProductCreateRequest.class)))
                .thenReturn(100L);

        mockMvc.perform(post("/products")
                        .param("sellerId", sellerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Limited Hoodie",
                                  "price": 59000.00,
                                  "description": "Limited edition hoodie"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/products/100"))
                .andExpect(content().string("100"));

        ArgumentCaptor<ProductCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(ProductCreateRequest.class);
        verify(productService).create(eq(sellerId), requestCaptor.capture());

        ProductCreateRequest request = requestCaptor.getValue();
        assertEquals("Limited Hoodie", request.getName());
        assertEquals(new BigDecimal("59000.00"), request.getPrice());
        assertEquals("Limited edition hoodie", request.getDescription());
    }

    @Test
    @DisplayName("판매자 ID가 없으면 400 상태를 반환한다")
    void rejectMissingSellerId() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Product",
                                  "price": 10000.00,
                                  "description": "Description"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("상품명이 공백이면 400 상태를 반환한다")
    void rejectBlankName() throws Exception {
        mockMvc.perform(post("/products")
                        .param("sellerId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "price": 59000.00,
                                  "description": "Description"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("상품 가격이 0이면 400 상태를 반환한다")
    void rejectZeroPrice() throws Exception {
        mockMvc.perform(post("/products")
                        .param("sellerId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Product",
                                  "price": 0,
                                  "description": "Description"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("요청 본문이 없으면 400 상태를 반환한다")
    void rejectMissingBody() throws Exception {
        mockMvc.perform(post("/products")
                        .param("sellerId", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("존재하지 않는 판매자로 상품을 등록하면 404 오류 응답을 반환한다")
    void rejectMissingSeller() throws Exception {
        when(productService.create(eq(999L), any(ProductCreateRequest.class)))
                .thenThrow(new ServiceException(ProductErrorCode.SELLER_NOT_FOUND));

        mockMvc.perform(post("/products")
                        .param("sellerId", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Product",
                                  "price": 10000.00,
                                  "description": "Description"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SELLER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 판매자입니다."));
    }

    @Test
    @DisplayName("판매자 역할이 아니면 상품 등록 시 403 오류 응답을 반환한다")
    void rejectNonSeller() throws Exception {
        when(productService.create(eq(1L), any(ProductCreateRequest.class)))
                .thenThrow(new ServiceException(ProductErrorCode.SELLER_ROLE_REQUIRED));

        mockMvc.perform(post("/products")
                        .param("sellerId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Product",
                                  "price": 10000.00,
                                  "description": "Description"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_REQUIRED"))
                .andExpect(jsonPath("$.message").value("판매자만 상품을 등록할 수 있습니다."));
    }

    @Test
    @DisplayName("존재하지 않는 상품을 조회하면 404 오류 응답을 반환한다")
    void rejectMissingProduct() throws Exception {
        when(productService.getProduct(999L))
                .thenThrow(new ServiceException(ProductErrorCode.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/products/{productId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 상품입니다."));
    }

    @Test
    @DisplayName("전체 상품 목록을 조회하면 200 상태와 상품 목록을 반환한다")
    void getProducts() throws Exception {
        User seller = new User(
                "seller@example.com",
                "encoded-password",
                "seller",
                UserRole.SELLER
        );
        ReflectionTestUtils.setField(seller, "id", 1L);

        Product product = new Product(
                seller,
                "Limited Hoodie",
                new BigDecimal("59000.00"),
                "Limited edition hoodie",
                null
        );
        ReflectionTestUtils.setField(product, "id", 100L);

        when(productService.getProducts())
                .thenReturn(List.of(new ProductResponse(product)));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100L))
                .andExpect(jsonPath("$[0].sellerId").value(1L))
                .andExpect(jsonPath("$[0].sellerName").value("seller"))
                .andExpect(jsonPath("$[0].name").value("Limited Hoodie"))
                .andExpect(jsonPath("$[0].price").value(59000.00))
                .andExpect(jsonPath("$[0].description").value("Limited edition hoodie"));

        verify(productService).getProducts();
    }

    @Test
    @DisplayName("상품을 수정하면 200 상태와 수정된 상품 정보를 반환한다")
    void updateProduct() throws Exception {
        Long sellerId = 1L;
        Long productId = 100L;
        User seller = new User(
                "seller@example.com",
                "encoded-password",
                "seller",
                UserRole.SELLER
        );
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Product product = new Product(
                seller,
                "Updated Product",
                new BigDecimal("49000.00"),
                "Updated description",
                null
        );
        ReflectionTestUtils.setField(product, "id", productId);

        when(productService.update(eq(sellerId), eq(productId), any(ProductUpdateRequest.class)))
                .thenReturn(new ProductResponse(product));

        mockMvc.perform(put("/products/{productId}", productId)
                        .param("sellerId", sellerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Product",
                                  "price": 49000.00,
                                  "description": "Updated description"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.sellerId").value(sellerId))
                .andExpect(jsonPath("$.name").value("Updated Product"))
                .andExpect(jsonPath("$.price").value(49000.00))
                .andExpect(jsonPath("$.description").value("Updated description"));

        ArgumentCaptor<ProductUpdateRequest> requestCaptor =
                ArgumentCaptor.forClass(ProductUpdateRequest.class);
        verify(productService).update(eq(sellerId), eq(productId), requestCaptor.capture());

        ProductUpdateRequest request = requestCaptor.getValue();
        assertEquals("Updated Product", request.getName());
        assertEquals(new BigDecimal("49000.00"), request.getPrice());
        assertEquals("Updated description", request.getDescription());
    }

    @Test
    @DisplayName("상품 수정 요청의 상품명이 공백이면 400 상태를 반환한다")
    void rejectUpdatingWithBlankName() throws Exception {
        mockMvc.perform(put("/products/{productId}", 100L)
                        .param("sellerId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "price": 49000.00,
                                  "description": "Updated description"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("상품 소유자가 아니면 수정 시 403 오류 응답을 반환한다")
    void rejectUpdatingAnotherSellersProduct() throws Exception {
        when(productService.update(eq(2L), eq(100L), any(ProductUpdateRequest.class)))
                .thenThrow(new ServiceException(ProductErrorCode.PRODUCT_OWNER_REQUIRED));

        mockMvc.perform(put("/products/{productId}", 100L)
                        .param("sellerId", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Product",
                                  "price": 49000.00,
                                  "description": "Updated description"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_OWNER_REQUIRED"))
                .andExpect(jsonPath("$.message").value("상품 소유자만 변경할 수 있습니다."));
    }

    @Test
    @DisplayName("상품을 삭제하면 204 상태를 반환한다")
    void deleteProduct() throws Exception {
        Long sellerId = 1L;
        Long productId = 100L;

        mockMvc.perform(delete("/products/{productId}", productId)
                        .param("sellerId", sellerId.toString()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(productService).delete(sellerId, productId);
    }
}
