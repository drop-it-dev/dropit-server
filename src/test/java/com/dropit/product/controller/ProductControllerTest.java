package com.dropit.product.controller;

import com.dropit.product.dto.request.ProductCreateRequest;
import com.dropit.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerTest {

    private MockMvc mockMvc;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        ProductController productController = new ProductController(productService);
        mockMvc = MockMvcBuilders.standaloneSetup(productController).build();
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
}
