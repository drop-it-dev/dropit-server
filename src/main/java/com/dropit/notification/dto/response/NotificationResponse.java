package com.dropit.notification.dto.response;

import com.dropit.notification.entity.Notification;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NotificationResponse {

    private final Long id;
    private final String title;
    private final String message;
    private final boolean read;
    private final LocalDateTime createdAt;

    public NotificationResponse(Notification notification) {
        this.id = notification.getId();
        this.title = notification.getTitle();
        this.message = notification.getMessage();
        this.read = notification.isRead();
        this.createdAt = notification.getCreatedAt();
    }
}
