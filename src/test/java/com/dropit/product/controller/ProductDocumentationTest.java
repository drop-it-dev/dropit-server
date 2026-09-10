package com.dropit.product.controller;

import com.dropit.documentation.DocumentationTestSupport;
import com.epages.restdocs.apispec.Schema;
import com.dropit.product.dto.response.ProductResponse;
import com.dropit.product.entity.Product;
import com.dropit.product.service.ProductService;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.ResourceSnippetParameters.builder;
import static org.springframework.restdocs.payload.JsonFieldType.NUMBER;
import static org.springframework.restdocs.payload.JsonFieldType.STRING;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.partWithName;
import static org.springframework.restdocs.request.RequestDocumentation.requestParts;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(RestDocumentationExtension.class)
class ProductDocumentationTest extends DocumentationTestSupport {

    private ProductService productService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        productService = mock(ProductService.class);
        configure(restDocumentation, new ProductController(productService), new PageableHandlerMethodArgumentResolver());
    }

    @Test
    void create() throws Exception {
        when(productService.create(eq(1L), any())).thenReturn(100L);
        mockMvc.perform(authenticated(post("/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Limited Hoodie\",\"description\":\"Limited edition hoodie\"}")))
                .andExpect(status().isCreated())
                .andDo(document("products-create", resource(builder()
                        .tag("Products").summary("상품 생성").requestHeaders(authorizationHeader())
                        .requestFields(fieldWithPath("name").description("상품명"), fieldWithPath("description").optional().description("상품 설명"))
                        .build())));
    }

    @Test
    void getProduct() throws Exception {
        when(productService.getProduct(100L)).thenReturn(productResponse());
        mockMvc.perform(authenticated(get("/products/{productId}", 100L)))
                .andExpect(status().isOk())
                .andDo(document("products-get", resource(builder()
                        .tag("Products").summary("상품 단건 조회").requestHeaders(authorizationHeader())
                        .responseFields(productFields()).build())));
    }

    @Test
    void getProducts() throws Exception {
        when(productService.getProducts(any())).thenReturn(new PageImpl<>(List.of(productResponse()), PageRequest.of(0, 20), 1));
        mockMvc.perform(authenticated(get("/products").param("page", "0").param("size", "20")))
                .andExpect(status().isOk())
                .andDo(document("products-get-all", resource(builder()
                        .tag("Products").summary("상품 목록 조회").description("상품 목록을 페이징하여 조회합니다.")
                        .requestHeaders(authorizationHeader())
                        .queryParameters(parameterWithName("page").description("페이지 번호"), parameterWithName("size").description("페이지 크기"), parameterWithName("sort").optional().description("정렬 조건"))
                        .responseFields(pageFields()).build())));
    }

    @Test
    void getProductsBySeller() throws Exception {
        when(productService.getProductsBySeller(eq(1L), any())).thenReturn(new PageImpl<>(List.of(productResponse()), PageRequest.of(0, 20), 1));
        mockMvc.perform(authenticated(get("/sellers/{sellerId}/products", 1L).param("page", "0").param("size", "20")))
                .andExpect(status().isOk())
                .andDo(document("products-get-by-seller", resource(builder()
                        .tag("Products").summary("판매자 상품 목록 조회").requestHeaders(authorizationHeader())
                        .queryParameters(parameterWithName("page").description("페이지 번호"), parameterWithName("size").description("페이지 크기"), parameterWithName("sort").optional().description("정렬 조건"))
                        .responseFields(pageFields()).build())));
    }

    @Test
    void update() throws Exception {
        when(productService.update(eq(1L), eq(100L), any())).thenReturn(productResponse());
        mockMvc.perform(authenticated(put("/products/{productId}", 100L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Hoodie\",\"description\":\"Updated description\"}")))
                .andExpect(status().isOk())
                .andDo(document("products-update", resource(builder()
                        .tag("Products").summary("상품 수정").requestHeaders(authorizationHeader())
                        .requestFields(fieldWithPath("name").description("상품명"), fieldWithPath("description").optional().description("상품 설명"))
                        .responseFields(productFields()).build())));
    }

    @Test
    void deleteProduct() throws Exception {
        mockMvc.perform(authenticated(delete("/products/{productId}", 100L)))
                .andExpect(status().isNoContent())
                .andDo(document("products-delete", resource(builder()
                        .tag("Products").summary("상품 삭제").requestHeaders(authorizationHeader()).build())));
    }

    @Test
    void uploadImage() throws Exception {
        when(productService.uploadImage(eq(1L), eq(100L), any())).thenReturn(productResponse());
        MockMultipartFile file = new MockMultipartFile("file", "product.jpg", "image/jpeg", "image".getBytes());
        mockMvc.perform(multipart("/products/{productId}/image", 100L).file(file).content(" ").with(bearerToken()).with(request -> {
            request.setMethod("PUT");
            return request;
        }))
                .andExpect(status().isOk())
                .andDo(document("products-upload-image", resource(builder()
                        .tag("Products").summary("상품 이미지 업로드").requestHeaders(authorizationHeader())
                        .requestSchema(new Schema("MultipartImage"))
                        .responseFields(productFields()).build()),
                        requestParts(partWithName("file").description("업로드할 이미지 파일"))));
    }

    private ProductResponse productResponse() {
        User seller = new User("seller@example.com", "encoded-password", "seller", UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", 1L);
        Product product = new Product(seller, "Limited Hoodie", "Limited edition hoodie", null);
        ReflectionTestUtils.setField(product, "id", 100L);
        return new ProductResponse(product, "https://cdn.example.com/product.jpg");
    }

    private org.springframework.restdocs.payload.FieldDescriptor[] productFields() {
        return new org.springframework.restdocs.payload.FieldDescriptor[]{
                fieldWithPath("id").type(NUMBER).description("상품 ID"),
                fieldWithPath("sellerId").type(NUMBER).description("판매자 ID"),
                fieldWithPath("sellerName").type(STRING).description("판매자 이름"),
                fieldWithPath("name").type(STRING).description("상품명"),
                fieldWithPath("description").type(STRING).optional().description("상품 설명"),
                fieldWithPath("imageUrl").type(STRING).optional().description("상품 이미지 URL"),
                fieldWithPath("createdAt").type(STRING).optional().description("생성 시각"),
                fieldWithPath("updatedAt").type(STRING).optional().description("수정 시각")
        };
    }

    private org.springframework.restdocs.payload.FieldDescriptor[] pageFields() {
        return new org.springframework.restdocs.payload.FieldDescriptor[]{
                fieldWithPath("content").description("상품 목록"),
                fieldWithPath("content[].id").type(NUMBER).description("상품 ID"),
                fieldWithPath("content[].sellerId").type(NUMBER).description("판매자 ID"),
                fieldWithPath("content[].sellerName").type(STRING).description("판매자 이름"),
                fieldWithPath("content[].name").type(STRING).description("상품명"),
                fieldWithPath("content[].description").type(STRING).optional().description("상품 설명"),
                fieldWithPath("content[].imageUrl").type(STRING).optional().description("상품 이미지 URL"),
                fieldWithPath("content[].createdAt").type(STRING).optional().description("생성 시각"),
                fieldWithPath("content[].updatedAt").type(STRING).optional().description("수정 시각"),
                fieldWithPath("pageable").description("페이징 정보"),
                fieldWithPath("pageable.*").ignored(),
                fieldWithPath("pageable.sort").ignored(),
                fieldWithPath("pageable.sort.*").ignored(),
                fieldWithPath("last").description("마지막 페이지 여부"),
                fieldWithPath("totalElements").description("전체 요소 수"),
                fieldWithPath("totalPages").description("전체 페이지 수"),
                fieldWithPath("size").description("페이지 크기"),
                fieldWithPath("number").description("현재 페이지 번호"),
                fieldWithPath("sort").description("정렬 정보"),
                fieldWithPath("sort.*").ignored(),
                fieldWithPath("first").description("첫 페이지 여부"),
                fieldWithPath("numberOfElements").description("현재 페이지 요소 수"),
                fieldWithPath("empty").description("빈 페이지 여부")
        };
    }
}
