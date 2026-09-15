package com.dropit.notification.scheduler;

import com.dropit.drop.entity.Drop;
import com.dropit.drop.repository.DropRepository;
import com.dropit.notification.service.WishlistNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WishlistNotificationScheduler {

    private final DropRepository dropRepository;
    private final WishlistNotificationService wishlistNotificationService;

    @Scheduled(fixedDelay = 60_000)
    public void sendUpcomingDropNotifications() {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime from = now.plusMinutes(9);
        LocalDateTime to = now.plusMinutes(10);

        List<Drop> drops =
                dropRepository.findAllByVisibleTrueAndOpenAtBetween(from, to);

        for (Drop drop : drops) {
            wishlistNotificationService.sendToWishlistUsers(
                    drop.getProduct().getId(),
                    "판매 시작 임박",
                    drop.getProduct().getName() + " 상품이 곧 판매 시작됩니다."
            );
        }
    }
}