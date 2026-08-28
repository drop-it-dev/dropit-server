package com.dropit.sellerprofile.repository;

import com.dropit.sellerprofile.entity.SellerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {
}
