package com.dropit.sellerprofile.controller;

import com.dropit.documentation.DocumentationTestSupport;
import com.dropit.sellerprofile.dto.response.SellerProfileResponse;
import com.dropit.sellerprofile.entity.SellerProfile;
import com.dropit.sellerprofile.service.SellerProfileService;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.epages.restdocs.apispec.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.ResourceSnippetParameters.builder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.payload.JsonFieldType.NUMBER;
import static org.springframework.restdocs.payload.JsonFieldType.STRING;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(RestDocumentationExtension.class)
class SellerProfileDocumentationTest extends DocumentationTestSupport {

    private SellerProfileService sellerProfileService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        sellerProfileService = mock(SellerProfileService.class);
        configure(restDocumentation, new SellerProfileController(sellerProfileService));
    }

    @Test
    void create() throws Exception {
        when(sellerProfileService.create(eq(1L), any())).thenReturn(response());
        mockMvc.perform(authenticated(post("/seller-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"소개\",\"instagramUrl\":\"https://instagram.com/seller\",\"youtubeUrl\":\"https://youtube.com/@seller\"}")))
                .andExpect(status().isCreated())
                .andDo(document("seller-profiles-create", resource(builder()
                        .tag("Seller Profiles").summary("판매자 프로필 생성").description("로그인한 판매자의 프로필을 생성합니다.").requestHeaders(authorizationHeader())
                        .requestSchema(new Schema("SellerProfileCreateRequest"))
                        .requestFields(
                                fieldWithPath("description").optional().description("판매자 소개"),
                                fieldWithPath("instagramUrl").optional().description("인스타그램 URL"),
                                fieldWithPath("youtubeUrl").optional().description("유튜브 URL")
                        ).responseSchema(new Schema("SellerProfileResponse")).responseFields(profileFields()).build())));
    }

    @Test
    void getMine() throws Exception {
        when(sellerProfileService.getMine(1L)).thenReturn(response());
        mockMvc.perform(authenticated(get("/seller-profiles/me")))
                .andExpect(status().isOk())
                .andDo(document("seller-profiles-get-me", resource(builder()
                        .tag("Seller Profiles").summary("내 판매자 프로필 조회").requestHeaders(authorizationHeader()).responseSchema(new Schema("SellerProfileResponse")).responseFields(profileFields()).build())));
    }

    @Test
    void getById() throws Exception {
        when(sellerProfileService.getById(10L)).thenReturn(response());
        mockMvc.perform(authenticated(get("/seller-profiles/{sellerProfileId}", 10L)))
                .andExpect(status().isOk())
                .andDo(document("seller-profiles-get", resource(builder()
                        .tag("Seller Profiles").summary("판매자 프로필 조회").requestHeaders(authorizationHeader())
                        .pathParameters(parameterWithName("sellerProfileId").description("판매자 프로필 ID"))
                        .responseSchema(new Schema("SellerProfileResponse")).responseFields(profileFields()).build())));
    }

    @Test
    void update() throws Exception {
        when(sellerProfileService.update(eq(1L), any())).thenReturn(response());
        mockMvc.perform(authenticated(patch("/seller-profiles/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"수정된 소개\",\"instagramUrl\":\"https://instagram.com/new\",\"youtubeUrl\":\"https://youtube.com/@new\"}")))
                .andExpect(status().isOk())
                .andDo(document("seller-profiles-update", resource(builder()
                        .tag("Seller Profiles").summary("판매자 프로필 수정").requestHeaders(authorizationHeader())
                        .requestSchema(new Schema("SellerProfileUpdateRequest"))
                        .requestFields(
                                fieldWithPath("description").optional().description("판매자 소개"),
                                fieldWithPath("instagramUrl").optional().description("인스타그램 URL"),
                                fieldWithPath("youtubeUrl").optional().description("유튜브 URL")
                        ).responseSchema(new Schema("SellerProfileResponse")).responseFields(profileFields()).build())));
    }

    @Test
    void uploadImage() throws Exception {
        when(sellerProfileService.uploadImage(eq(1L), any())).thenReturn(response());
        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", "image".getBytes());
        mockMvc.perform(multipart("/seller-profiles/me/image").file(file).content(" ").with(bearerToken()).with(request -> {
            request.setMethod("PUT");
            return request;
        }))
                .andExpect(status().isOk())
                .andDo(document("seller-profiles-upload-image", resource(builder()
                        .tag("Seller Profiles").summary("판매자 프로필 이미지 업로드").description("프로필 이미지를 업로드합니다.").requestHeaders(authorizationHeader())
                        .requestSchema(new Schema("MultipartImage"))
                        .responseSchema(new Schema("SellerProfileResponse")).responseFields(profileFields()).build()),
                        requestParts(partWithName("file").description("업로드할 이미지 파일"))));
    }

    @Test
    void deleteProfile() throws Exception {
        mockMvc.perform(authenticated(delete("/seller-profiles/me")))
                .andExpect(status().isNoContent())
                .andDo(document("seller-profiles-delete", resource(builder()
                        .tag("Seller Profiles").summary("판매자 프로필 삭제").requestHeaders(authorizationHeader()).build())));
    }

    private SellerProfileResponse response() {
        User seller = new User("seller@example.com", "encoded-password", "seller", UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", 1L);
        SellerProfile profile = new SellerProfile(seller, "소개", null, "https://instagram.com/seller", "https://youtube.com/@seller");
        ReflectionTestUtils.setField(profile, "id", 10L);
        return SellerProfileResponse.from(profile, "https://cdn.example.com/profile.jpg");
    }

    private org.springframework.restdocs.payload.FieldDescriptor[] profileFields() {
        return new org.springframework.restdocs.payload.FieldDescriptor[]{
                fieldWithPath("id").type(NUMBER).description("판매자 프로필 ID"),
                fieldWithPath("userId").type(NUMBER).description("사용자 ID"),
                fieldWithPath("description").type(STRING).optional().description("판매자 소개"),
                fieldWithPath("imageUrl").type(STRING).optional().description("프로필 이미지 URL"),
                fieldWithPath("instagramUrl").type(STRING).optional().description("인스타그램 URL"),
                fieldWithPath("youtubeUrl").type(STRING).optional().description("유튜브 URL")
        };
    }
}
