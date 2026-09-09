package com.dropit.notification.repository;

import com.dropit.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<Notification> findByIdAndUser_Id(Long notificationId, Long userId);
}
