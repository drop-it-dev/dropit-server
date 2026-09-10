package com.dropit.notification.controller;

import com.dropit.documentation.DocumentationTestSupport;
import com.dropit.notification.dto.response.NotificationResponse;
import com.dropit.notification.entity.Notification;
import com.dropit.notification.service.NotificationService;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
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
import static org.springframework.restdocs.payload.JsonFieldType.BOOLEAN;
import static org.springframework.restdocs.payload.JsonFieldType.NUMBER;
import static org.springframework.restdocs.payload.JsonFieldType.STRING;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(RestDocumentationExtension.class)
class NotificationDocumentationTest extends DocumentationTestSupport {

    private NotificationService notificationService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        notificationService = mock(NotificationService.class);
        configure(restDocumentation, new NotificationController(notificationService));
    }

    @Test
    void getMine() throws Exception {
        when(notificationService.getMine(1L)).thenReturn(List.of(response(false)));
        mockMvc.perform(authenticated(get("/notifications/me")))
                .andExpect(status().isOk())
                .andDo(document("notifications-get-me", resource(builder()
                        .tag("Notifications").summary("내 알림 목록 조회").requestHeaders(authorizationHeader())
                        .responseFields(
                                fieldWithPath("[]").type(ARRAY).description("알림 목록"),
                                fieldWithPath("[].id").type(NUMBER).description("알림 ID"),
                                fieldWithPath("[].title").type(STRING).description("알림 제목"),
                                fieldWithPath("[].message").type(STRING).description("알림 내용"),
                                fieldWithPath("[].read").type(BOOLEAN).description("읽음 여부"),
                                fieldWithPath("[].createdAt").type(STRING).optional().description("생성 시각")
                        ).build())));
    }

    @Test
    void markAsRead() throws Exception {
        when(notificationService.markAsRead(1L, 100L)).thenReturn(response(true));
        mockMvc.perform(authenticated(patch("/notifications/{notificationId}/read", 100L)))
                .andExpect(status().isOk())
                .andDo(document("notifications-mark-read", resource(builder()
                        .tag("Notifications").summary("알림 읽음 처리").requestHeaders(authorizationHeader())
                        .responseFields(
                                fieldWithPath("id").type(NUMBER).description("알림 ID"),
                                fieldWithPath("title").type(STRING).description("알림 제목"),
                                fieldWithPath("message").type(STRING).description("알림 내용"),
                                fieldWithPath("read").type(BOOLEAN).description("읽음 여부"),
                                fieldWithPath("createdAt").type(STRING).optional().description("생성 시각")
                        ).build())));
    }

    private NotificationResponse response(boolean read) {
        User user = new User("user@example.com", "encoded-password", "user", UserRole.USER);
        ReflectionTestUtils.setField(user, "id", 1L);
        Notification notification = new Notification(user, "주문 알림", "주문 상태가 변경되었습니다.");
        ReflectionTestUtils.setField(notification, "id", 100L);
        if (read) notification.markAsRead();
        return new NotificationResponse(notification);
    }
}
