package com.dropit.user.controller;

import com.dropit.documentation.DocumentationTestSupport;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.ResourceSnippetParameters.builder;
import static org.springframework.restdocs.payload.JsonFieldType.NUMBER;
import static org.springframework.restdocs.payload.JsonFieldType.STRING;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(RestDocumentationExtension.class)
class UserDocumentationTest extends DocumentationTestSupport {

    private UserService userService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        userService = mock(UserService.class);
        configure(restDocumentation, new UserController(userService));
    }

    @Test
    void getMyInfo() throws Exception {
        when(userService.findUser(1L)).thenReturn(user());
        mockMvc.perform(authenticated(get("/users/me")))
                .andExpect(status().isOk())
                .andDo(document("users-get-me", resource(builder()
                        .tag("Users").summary("내 정보 조회").description("현재 로그인한 사용자의 정보를 조회합니다.").requestHeaders(authorizationHeader())
                        .build())));
    }

    @Test
    void updateMyProfile() throws Exception {
        when(userService.updateProfile(org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(user());
        mockMvc.perform(authenticated(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"username\":\"new-user\"}")))
                .andExpect(status().isOk())
                .andDo(document("users-update-me", resource(builder()
                        .tag("Users").summary("내 정보 수정").description("현재 로그인한 사용자의 이메일과 이름을 수정합니다.").requestHeaders(authorizationHeader())
                        .requestFields(
                                fieldWithPath("email").optional().description("변경할 이메일 주소"),
                                fieldWithPath("username").optional().description("변경할 사용자 이름")
                        )
                        .responseFields(
                                fieldWithPath("id").type(NUMBER).description("사용자 ID"),
                                fieldWithPath("email").type(STRING).description("이메일 주소"),
                                fieldWithPath("username").type(STRING).description("사용자 이름"),
                                fieldWithPath("role").type(STRING).description("사용자 역할"),
                                fieldWithPath("createdAt").type(STRING).optional().description("가입 시각"),
                                fieldWithPath("updatedAt").type(STRING).optional().description("수정 시각")
                        ).build())));
    }

    @Test
    void updateMyPassword() throws Exception {
        mockMvc.perform(authenticated(patch("/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}")))
                .andExpect(status().isNoContent())
                .andDo(document("users-update-password", resource(builder()
                        .tag("Users").summary("비밀번호 변경").description("현재 비밀번호를 확인하고 새 비밀번호로 변경합니다.").requestHeaders(authorizationHeader())
                        .requestFields(
                                fieldWithPath("currentPassword").description("현재 비밀번호"),
                                fieldWithPath("newPassword").description("새 비밀번호 (8자 이상)")
                        ).build())));
    }

    @Test
    void deleteMyAccount() throws Exception {
        mockMvc.perform(authenticated(delete("/users/me")))
                .andExpect(status().isNoContent())
                .andDo(document("users-delete-me", resource(builder()
                        .tag("Users").summary("내 계정 삭제").description("현재 로그인한 사용자의 계정을 삭제합니다.").requestHeaders(authorizationHeader()).build())));
    }

    @Test
    void deleteUserByAdmin() throws Exception {
        mockMvc.perform(authenticated(delete("/users/{userId}", 2L)))
                .andExpect(status().isNoContent())
                .andDo(document("users-delete-by-admin", resource(builder()
                        .tag("Users").summary("사용자 삭제").description("관리자가 지정한 사용자를 삭제합니다.").requestHeaders(authorizationHeader()).build())));
    }

    private User user() {
        User user = new User("user@example.com", "encoded-password", "user", UserRole.USER);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
