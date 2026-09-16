package com.dropit.notification.messaging.dto;

public record NotificationEvent(
        Long userId,
        String title,
        String message
) {
}
