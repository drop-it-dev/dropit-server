package com.dropit.notification.controller;

import com.dropit.global.exception.GlobalExceptionHandler;
import com.dropit.global.security.authentication.JwtAuthenticationToken;
import com.dropit.global.security.principal.AuthUser;
import com.dropit.notification.dto.response.NotificationResponse;
import com.dropit.notification.entity.Notification;
import com.dropit.notification.service.NotificationService;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {

    private MockMvc mockMvc;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new NotificationController(notificationService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        new AuthUser(1L),
                        List.of()
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("현재 사용자의 알림 목록을 조회한다")
    void getMine() throws Exception {
        NotificationResponse response =
                createResponse(100L, false);

        when(notificationService.getMine(1L))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/notifications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100L))
                .andExpect(jsonPath("$[0].read").value(false));

        verify(notificationService).getMine(1L);
    }

    @Test
    @DisplayName("현재 사용자의 알림을 읽음 처리한다")
    void markAsRead() throws Exception {
        NotificationResponse response =
                createResponse(100L, true);

        when(notificationService.markAsRead(1L, 100L))
                .thenReturn(response);

        mockMvc.perform(
                        patch(
                                "/notifications/{notificationId}/read",
                                100L
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.read").value(true));

        verify(notificationService)
                .markAsRead(1L, 100L);
    }

    private NotificationResponse createResponse(
            Long notificationId,
            boolean read
    ) {
        User user = new User(
                "user@example.com",
                "password",
                "user",
                UserRole.USER
        );
        ReflectionTestUtils.setField(user, "id", 1L);

        Notification notification =
                new Notification(user, "알림", "내용");
        ReflectionTestUtils.setField(
                notification,
                "id",
                notificationId
        );

        if (read) {
            notification.markAsRead();
        }

        return new NotificationResponse(notification);
    }
}
