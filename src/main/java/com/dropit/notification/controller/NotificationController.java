package com.dropit.notification.controller;

import com.dropit.global.security.principal.CurrentUserId;
import com.dropit.notification.dto.response.NotificationResponse;
import com.dropit.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/me")
    public ResponseEntity<List<NotificationResponse>> getMine(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(
                notificationService.getMine(userId)
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @CurrentUserId Long userId,
            @PathVariable Long notificationId
    ) {
        return ResponseEntity.ok(
                notificationService.markAsRead(userId, notificationId)
        );
    }
}
