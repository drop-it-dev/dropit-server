package com.dropit.notification.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.notification.dto.response.NotificationResponse;
import com.dropit.notification.entity.Notification;
import com.dropit.notification.exception.NotificationErrorCode;
import com.dropit.notification.repository.NotificationRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("알림을 생성한다")
    void createNotification() {
        User user = createUser(1L);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    ReflectionTestUtils.setField(notification, "id", 100L);
                    return notification;
                });

        NotificationResponse response =
                notificationService.create(
                        1L,
                        "판매 시작 임박",
                        "찜한 상품의 판매 시작이 임박했습니다."
                );

        assertEquals(100L, response.getId());
        assertEquals("판매 시작 임박", response.getTitle());
        assertEquals(
                "찜한 상품의 판매 시작이 임박했습니다.",
                response.getMessage()
        );
        assertFalse(response.isRead());

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자에게 알림을 생성할 수 없다")
    void rejectCreateWhenUserNotFound() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.empty());

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> notificationService.create(
                        1L,
                        "알림",
                        "내용"
                )
        );

        assertEquals(
                NotificationErrorCode.USER_NOT_FOUND,
                exception.getErrorCode()
        );
        verify(notificationRepository, never())
                .save(any(Notification.class));
    }

    @Test
    @DisplayName("현재 사용자의 알림 목록을 조회한다")
    void getMine() {
        User user = createUser(1L);

        Notification first =
                new Notification(user, "첫 번째 알림", "첫 번째 내용");
        Notification second =
                new Notification(user, "두 번째 알림", "두 번째 내용");

        ReflectionTestUtils.setField(first, "id", 100L);
        ReflectionTestUtils.setField(second, "id", 101L);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(notificationRepository
                .findAllByUser_IdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(second, first));

        List<NotificationResponse> responses =
                notificationService.getMine(1L);

        assertEquals(2, responses.size());
        assertEquals(101L, responses.get(0).getId());
        assertEquals(100L, responses.get(1).getId());
    }

    @Test
    @DisplayName("현재 사용자의 알림을 읽음 처리한다")
    void markAsRead() {
        User user = createUser(1L);
        Notification notification =
                new Notification(user, "알림", "내용");

        ReflectionTestUtils.setField(notification, "id", 100L);

        when(notificationRepository
                .findByIdAndUser_Id(100L, 1L))
                .thenReturn(Optional.of(notification));

        NotificationResponse response =
                notificationService.markAsRead(1L, 100L);

        assertTrue(response.isRead());
        assertTrue(notification.isRead());
    }

    @Test
    @DisplayName("본인의 알림이 아니면 읽음 처리할 수 없다")
    void rejectMarkAsReadForAnotherUsersNotification() {
        when(notificationRepository
                .findByIdAndUser_Id(100L, 1L))
                .thenReturn(Optional.empty());

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> notificationService.markAsRead(1L, 100L)
        );

        assertEquals(
                NotificationErrorCode.NOTIFICATION_NOT_FOUND,
                exception.getErrorCode()
        );
    }

    private User createUser(Long id) {
        User user = new User(
                "user@example.com",
                "password",
                "user",
                UserRole.USER
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
