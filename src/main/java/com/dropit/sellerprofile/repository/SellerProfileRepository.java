package com.dropit.sellerprofile.repository;

import com.dropit.sellerprofile.entity.SellerProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

    Optional<SellerProfile> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);
}
