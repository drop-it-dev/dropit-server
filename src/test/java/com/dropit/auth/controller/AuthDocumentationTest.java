package com.dropit.auth.controller;

import com.dropit.auth.dto.response.TokenResponse;
import com.dropit.auth.service.AuthService;
import com.dropit.documentation.DocumentationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.ResourceSnippetParameters.builder;
import static org.springframework.restdocs.payload.JsonFieldType.STRING;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(RestDocumentationExtension.class)
class AuthDocumentationTest extends DocumentationTestSupport {

    private AuthService authService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        authService = mock(AuthService.class);
        configure(restDocumentation, new AuthController(authService));
    }

    @Test
    void signup() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123","username":"user","role":"USER"}
                                """))
                .andExpect(status().isCreated())
                .andDo(document("auth-signup", resource(builder()
                        .tag("Auth").summary("회원가입").description("사용자 계정을 생성합니다.")
                        .requestFields(
                                fieldWithPath("email").description("이메일 주소"),
                                fieldWithPath("password").description("8자 이상의 비밀번호"),
                                fieldWithPath("username").description("사용자 이름"),
                                fieldWithPath("role").description("사용자 역할 (USER 또는 SELLER)")
                        ).build())));
    }

    @Test
    void login() throws Exception {
        when(authService.login(any())).thenReturn(new TokenResponse("access-token", "refresh-token"));
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andDo(document("auth-login", resource(builder()
                        .tag("Auth").summary("로그인").description("이메일과 비밀번호로 로그인합니다.")
                        .requestFields(
                                fieldWithPath("email").description("이메일 주소"),
                                fieldWithPath("password").description("비밀번호")
                        )
                        .responseFields(
                                fieldWithPath("accessToken").type(STRING).description("접근 토큰"),
                                fieldWithPath("refreshToken").type(STRING).description("갱신 토큰")
                        ).build())));
    }

    @Test
    void reissue() throws Exception {
        when(authService.reissue(any())).thenReturn(new TokenResponse("new-access-token", "new-refresh-token"));
        mockMvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andDo(document("auth-reissue", resource(builder()
                        .tag("Auth").summary("토큰 재발급").description("Refresh Token으로 토큰을 재발급합니다.")
                        .requestFields(fieldWithPath("refreshToken").description("갱신 토큰"))
                        .responseFields(
                                fieldWithPath("accessToken").type(STRING).description("접근 토큰"),
                                fieldWithPath("refreshToken").type(STRING).description("갱신 토큰")
                        ).build())));
    }
}
