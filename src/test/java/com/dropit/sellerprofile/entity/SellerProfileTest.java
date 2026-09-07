package com.dropit.sellerprofile.entity;

import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SellerProfileTest {

    private final User seller = new User(
            "seller@example.com",
            "encoded-password",
            "seller",
            UserRole.SELLER
    );

    @Test
    @DisplayName("판매자 프로필을 생성하면 입력한 정보가 저장된다")
    void createSellerProfile() {
        SellerProfile sellerProfile = new SellerProfile(
                seller,
                "판매자 소개",
                "https://example.com/profile.png",
                "https://instagram.com/example",
                "https://youtube.com/@example"
        );

        assertEquals(seller, sellerProfile.getUser());
        assertEquals("판매자 소개", sellerProfile.getDescription());
        assertEquals("https://example.com/profile.png", sellerProfile.getImageUrl());
        assertEquals("https://instagram.com/example", sellerProfile.getInstagramUrl());
        assertEquals("https://youtube.com/@example", sellerProfile.getYoutubeUrl());
    }

    @Test
    @DisplayName("판매자 프로필 정보를 수정할 수 있다")
    void updateSellerProfile() {
        SellerProfile sellerProfile = new SellerProfile(
                seller, "기존 소개", null, null, null
        );

        sellerProfile.update(
                "수정된 소개",
                "https://example.com/updated.png",
                "https://instagram.com/updated",
                "https://youtube.com/@updated"
        );

        assertEquals("수정된 소개", sellerProfile.getDescription());
        assertEquals("https://example.com/updated.png", sellerProfile.getImageUrl());
        assertEquals("https://instagram.com/updated", sellerProfile.getInstagramUrl());
        assertEquals("https://youtube.com/@updated", sellerProfile.getYoutubeUrl());
    }
}
