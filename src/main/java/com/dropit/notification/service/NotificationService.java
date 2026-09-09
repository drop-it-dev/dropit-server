package com.dropit.notification.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.notification.dto.response.NotificationResponse;
import com.dropit.notification.entity.Notification;
import com.dropit.notification.exception.NotificationErrorCode;
import com.dropit.notification.repository.NotificationRepository;
import com.dropit.user.entity.User;
import com.dropit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public NotificationResponse create(
            Long userId,
            String title,
            String message
    ) {
        User user = findUser(userId);

        Notification notification =
                new Notification(user, title, message);

        Notification savedNotification =
                notificationRepository.save(notification);

        return new NotificationResponse(savedNotification);
    }

    public List<NotificationResponse> getMine(Long userId) {
        findUser(userId);

        return notificationRepository
                .findAllByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::new)
                .toList();
    }

    @Transactional
    public NotificationResponse markAsRead(
            Long userId,
            Long notificationId
    ) {
        Notification notification =
                notificationRepository
                        .findByIdAndUser_Id(notificationId, userId)
                        .orElseThrow(() ->
                                new ServiceException(
                                        NotificationErrorCode.NOTIFICATION_NOT_FOUND
                                )
                        );

        notification.markAsRead();

        return new NotificationResponse(notification);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ServiceException(
                                NotificationErrorCode.USER_NOT_FOUND
                        )
                );
    }
}
