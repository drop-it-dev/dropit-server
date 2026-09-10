package com.dropit.drop.controller;

import com.dropit.documentation.DocumentationTestSupport;
import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.service.DropService;
import com.dropit.product.entity.Product;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.ResourceSnippetParameters.builder;
import static org.springframework.restdocs.payload.JsonFieldType.NUMBER;
import static org.springframework.restdocs.payload.JsonFieldType.STRING;
import static org.springframework.restdocs.payload.JsonFieldType.BOOLEAN;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(RestDocumentationExtension.class)
class DropDocumentationTest extends DocumentationTestSupport {

    private DropService dropService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        dropService = mock(DropService.class);
        configure(restDocumentation, new DropController(dropService), new PageableHandlerMethodArgumentResolver());
    }

    @Test
    void create() throws Exception {
        when(dropService.save(eq(1L), any())).thenReturn(100L);
        mockMvc.perform(authenticated(post("/drops").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10,\"price\":59000,\"initialQuantity\":100,\"discountRate\":20,\"purchaseLimit\":2,\"openAt\":\"2030-01-01T10:00:00\",\"closeAt\":\"2030-01-02T10:00:00\"}")))
                .andExpect(status().isCreated())
                .andDo(document("drops-create", resource(builder()
                        .tag("Drops").summary("드랍 생성")
                        .requestFields(
                                fieldWithPath("productId").type(NUMBER).description("상품 ID"),
                                fieldWithPath("price").type(NUMBER).description("판매 가격"),
                                fieldWithPath("initialQuantity").type(NUMBER).description("초기 수량"),
                                fieldWithPath("discountRate").type(NUMBER).description("할인율"),
                                fieldWithPath("purchaseLimit").type(NUMBER).description("구매 제한 수량"),
                                fieldWithPath("openAt").type(STRING).description("판매 시작 시각"),
                                fieldWithPath("closeAt").type(STRING).description("판매 종료 시각")
                        ).build())));
    }

    @Test
    void getAll() throws Exception {
        when(dropService.getAll(any(), any())).thenReturn(new PageImpl<>(List.of(dropResponse()), PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/drops").param("keyword", "hoodie").param("status", "OPEN").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andDo(document("drops-get-all", resource(builder()
                        .tag("Drops").summary("드랍 목록 조회").description("검색 조건과 페이징 조건으로 드랍을 조회합니다.").build())));
    }

    @Test
    void getOne() throws Exception {
        when(dropService.getOne(100L)).thenReturn(dropResponse());
        mockMvc.perform(get("/drops/{dropId}", 100L))
                .andExpect(status().isOk())
                .andDo(document("drops-get", resource(builder()
                        .tag("Drops").summary("드랍 단건 조회").responseFields(dropFields()).build())));
    }

    @Test
    void getPublicDropsBySeller() throws Exception {
        when(dropService.getPublicDropsBySeller(eq(1L), any())).thenReturn(new PageImpl<>(List.of(dropResponse()), PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/creators/{sellerId}/drops", 1L).param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andDo(document("drops-get-by-creator", resource(builder()
                        .tag("Drops").summary("판매자 공개 드랍 목록 조회").build())));
    }

    @Test
    void getDropsForSellerManagement() throws Exception {
        when(dropService.getDropsForSellerManagement(eq(1L), any())).thenReturn(new PageImpl<>(List.of(dropResponse()), PageRequest.of(0, 20), 1));
        mockMvc.perform(authenticated(get("/users/me/drops")).param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andDo(document("drops-get-my-drops", resource(builder()
                        .tag("Drops").summary("내 드랍 목록 조회").build())));
    }

    @Test
    void update() throws Exception {
        when(dropService.update(eq(1L), eq(100L), any())).thenReturn(dropResponse());
        mockMvc.perform(authenticated(patch("/drops/{dropId}", 100L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":60000,\"initialQuantity\":120,\"discountRate\":10,\"purchaseLimit\":3,\"openAt\":\"2030-01-01T10:00:00\",\"closeAt\":\"2030-01-02T10:00:00\"}")))
                .andExpect(status().isOk())
                .andDo(document("drops-update", resource(builder()
                        .tag("Drops").summary("드랍 수정")
                        .requestFields(
                                fieldWithPath("price").optional().type(NUMBER).description("판매 가격"),
                                fieldWithPath("initialQuantity").optional().type(NUMBER).description("초기 수량"),
                                fieldWithPath("discountRate").optional().type(NUMBER).description("할인율"),
                                fieldWithPath("purchaseLimit").optional().type(NUMBER).description("구매 제한 수량"),
                                fieldWithPath("openAt").optional().type(STRING).description("판매 시작 시각"),
                                fieldWithPath("closeAt").optional().type(STRING).description("판매 종료 시각")
                        ).responseFields(dropFields()).build())));
    }

    @Test
    void changeVisibility() throws Exception {
        when(dropService.changeVisibility(eq(1L), eq(100L), any())).thenReturn(dropResponse());
        mockMvc.perform(authenticated(patch("/drops/{dropId}/visibility", 100L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":true}")))
                .andExpect(status().isOk())
                .andDo(document("drops-change-visibility", resource(builder()
                        .tag("Drops").summary("드랍 공개 여부 변경")
                        .requestFields(fieldWithPath("visible").type(BOOLEAN).description("공개 여부"))
                        .responseFields(dropFields()).build())));
    }

    @Test
    void deleteDrop() throws Exception {
        mockMvc.perform(authenticated(delete("/drops/{dropId}", 100L)))
                .andExpect(status().isNoContent())
                .andDo(document("drops-delete", resource(builder()
                        .tag("Drops").summary("드랍 삭제").build())));
    }

    private DropResponse dropResponse() {
        User seller = new User("seller@example.com", "encoded-password", "seller", UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", 1L);
        Product product = new Product(seller, "Limited Hoodie", "Limited edition hoodie", "products/10/image.jpg");
        ReflectionTestUtils.setField(product, "id", 10L);
        Drop drop = new Drop(product, new BigDecimal("59000"), 100, 20, 2,
                LocalDateTime.of(2030, 1, 1, 10, 0), LocalDateTime.of(2030, 1, 2, 10, 0));
        ReflectionTestUtils.setField(drop, "id", 100L);
        drop.changeVisibility(true);
        return DropResponse.from(drop);
    }

    private org.springframework.restdocs.payload.FieldDescriptor[] dropFields() {
        return new org.springframework.restdocs.payload.FieldDescriptor[]{
                fieldWithPath("id").type(NUMBER).description("드랍 ID"),
                fieldWithPath("sellerId").type(NUMBER).description("판매자 ID"),
                fieldWithPath("sellerName").type(STRING).description("판매자 이름"),
                fieldWithPath("productId").type(NUMBER).description("상품 ID"),
                fieldWithPath("productName").type(STRING).description("상품명"),
                fieldWithPath("imageUrl").type(STRING).optional().description("상품 이미지 URL"),
                fieldWithPath("price").type(NUMBER).description("판매 가격"),
                fieldWithPath("discountRate").type(NUMBER).description("할인율"),
                fieldWithPath("initialQuantity").type(NUMBER).description("초기 수량"),
                fieldWithPath("remainingQuantity").type(NUMBER).description("잔여 수량"),
                fieldWithPath("soldQuantity").type(NUMBER).description("판매 수량"),
                fieldWithPath("purchaseLimit").type(NUMBER).description("구매 제한 수량"),
                fieldWithPath("visible").type(BOOLEAN).description("공개 여부"),
                fieldWithPath("openAt").type(STRING).description("판매 시작 시각"),
                fieldWithPath("closeAt").type(STRING).description("판매 종료 시각"),
                fieldWithPath("status").type(STRING).description("드랍 상태")
        };
    }
}
