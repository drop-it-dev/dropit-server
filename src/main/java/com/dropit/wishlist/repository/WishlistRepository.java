package com.dropit.wishlist.repository;

import com.dropit.wishlist.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    boolean existsByUser_IdAndProduct_Id(Long userId, Long productId);

    Optional<Wishlist> findByUser_IdAndProduct_Id(Long userId, Long productId);

    List<Wishlist> findAllByUser_Id(Long userId);
}
