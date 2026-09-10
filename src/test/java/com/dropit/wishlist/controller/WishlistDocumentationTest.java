package com.dropit.wishlist.controller;

import com.dropit.documentation.DocumentationTestSupport;
import com.epages.restdocs.apispec.Schema;
import com.dropit.product.entity.Product;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.wishlist.dto.response.WishlistResponse;
import com.dropit.wishlist.entity.Wishlist;
import com.dropit.wishlist.service.WishlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.ResourceSnippetParameters.builder;
import static org.springframework.restdocs.payload.JsonFieldType.ARRAY;
import static org.springframework.restdocs.payload.JsonFieldType.NUMBER;
import static org.springframework.restdocs.payload.JsonFieldType.STRING;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(RestDocumentationExtension.class)
class WishlistDocumentationTest extends DocumentationTestSupport {

    private WishlistService wishlistService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        wishlistService = mock(WishlistService.class);
        configure(restDocumentation, new WishlistController(wishlistService));
    }

    @Test
    void add() throws Exception {
        when(wishlistService.add(1L, 10L)).thenReturn(response());
        mockMvc.perform(authenticated(post("/wishlists/{productId}", 10L)))
                .andExpect(status().isOk())
                .andDo(document("wishlists-add", resource(builder()
                        .tag("Wishlists").summary("상품 찜하기").requestHeaders(authorizationHeader())
                        .pathParameters(parameterWithName("productId").description("상품 ID"))
                        .responseSchema(new Schema("WishlistResponse")).responseFields(wishlistFields()).build())));
    }

    @Test
    void getMine() throws Exception {
        when(wishlistService.getMine(1L)).thenReturn(List.of(response()));
        mockMvc.perform(authenticated(get("/wishlists/me")))
                .andExpect(status().isOk())
                .andDo(document("wishlists-get-me", resource(builder()
                        .tag("Wishlists").summary("내 찜 목록 조회").requestHeaders(authorizationHeader())
                        .responseSchema(new Schema("WishlistList"))
                        .responseFields(
                                fieldWithPath("[]").type(ARRAY).description("찜 목록"),
                                fieldWithPath("[].id").type(NUMBER).description("찜 ID"),
                                fieldWithPath("[].productId").type(NUMBER).description("상품 ID"),
                                fieldWithPath("[].productName").type(STRING).description("상품명"),
                                fieldWithPath("[].productImageUrl").type(STRING).optional().description("상품 이미지 URL"),
                                fieldWithPath("[].createdAt").type(STRING).optional().description("찜 생성 시각")
                        ).build())));
    }

    @Test
    void deleteWishlist() throws Exception {
        mockMvc.perform(authenticated(delete("/wishlists/{productId}", 10L)))
                .andExpect(status().isNoContent())
                .andDo(document("wishlists-delete", resource(builder()
                        .tag("Wishlists").summary("상품 찜 삭제").requestHeaders(authorizationHeader())
                        .pathParameters(parameterWithName("productId").description("상품 ID")).build())));
    }

    private WishlistResponse response() {
        User user = new User("user@example.com", "encoded-password", "user", UserRole.USER);
        ReflectionTestUtils.setField(user, "id", 1L);
        Product product = new Product(user, "Limited Hoodie", "Limited edition hoodie", "products/10/image.jpg");
        ReflectionTestUtils.setField(product, "id", 10L);
        Wishlist wishlist = new Wishlist(user, product);
        ReflectionTestUtils.setField(wishlist, "id", 100L);
        return new WishlistResponse(wishlist);
    }

    private org.springframework.restdocs.payload.FieldDescriptor[] wishlistFields() {
        return new org.springframework.restdocs.payload.FieldDescriptor[]{
                fieldWithPath("id").type(NUMBER).description("찜 ID"),
                fieldWithPath("productId").type(NUMBER).description("상품 ID"),
                fieldWithPath("productName").type(STRING).description("상품명"),
                fieldWithPath("productImageUrl").type(STRING).optional().description("상품 이미지 URL"),
                fieldWithPath("createdAt").type(STRING).optional().description("찜 생성 시각")
        };
    }
}
