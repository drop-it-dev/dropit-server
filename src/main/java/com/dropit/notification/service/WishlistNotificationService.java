package com.dropit.notification.service;

import com.dropit.notification.messaging.dto.NotificationEvent;
import com.dropit.notification.messaging.producer.NotificationProducer;
import com.dropit.wishlist.entity.Wishlist;
import com.dropit.wishlist.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistNotificationService {

    private final WishlistRepository wishlistRepository;
    private final NotificationProducer notificationProducer;

    public void sendToWishlistUsers(
            Long productId,
            String title,
            String message
    ) {
        List<Wishlist> wishlists =
                wishlistRepository.findAllByProduct_Id(productId);

        wishlists.forEach(wishlist ->
                notificationProducer.send(
                        new NotificationEvent(
                                wishlist.getUser().getId(),
                                title,
                                message
                        )
                )
        );
    }
}