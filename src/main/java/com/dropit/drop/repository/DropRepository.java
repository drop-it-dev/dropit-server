package com.dropit.drop.repository;

import com.dropit.drop.entity.Drop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DropRepository extends JpaRepository<Drop, Long> {

    Page<Drop> findAllByProductSellerIdAndVisibleTrue(
            Long sellerId,
            Pageable pageable
    );

    Page<Drop> findAllByProductSellerId(
            Long sellerId,
            Pageable pageable
    );
}
