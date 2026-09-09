package com.dropit.wishlist.controller;

import com.dropit.global.exception.GlobalExceptionHandler;
import com.dropit.global.security.authentication.JwtAuthenticationToken;
import com.dropit.global.security.principal.AuthUser;
import com.dropit.wishlist.service.WishlistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WishlistControllerTest {

    private MockMvc mockMvc;
    private WishlistService wishlistService;

    @BeforeEach
    void setUp() {
        wishlistService = mock(WishlistService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new WishlistController(wishlistService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(new AuthUser(1L), List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("현재 사용자가 상품을 찜한다")
    void addWishlist() throws Exception {
        mockMvc.perform(post("/wishlists/{productId}", 10L))
                .andExpect(status().isOk());

        verify(wishlistService).add(1L, 10L);
    }

    @Test
    @DisplayName("현재 사용자의 찜 목록을 조회한다")
    void getMine() throws Exception {
        when(wishlistService.getMine(1L))
                .thenReturn(List.of());

        mockMvc.perform(get("/wishlists/me"))
                .andExpect(status().isOk());

        verify(wishlistService).getMine(1L);
    }

    @Test
    @DisplayName("현재 사용자가 찜한 상품을 삭제한다")
    void deleteWishlist() throws Exception {
        mockMvc.perform(delete("/wishlists/{productId}", 10L))
                .andExpect(status().isNoContent());

        verify(wishlistService).delete(1L, 10L);
    }
}
