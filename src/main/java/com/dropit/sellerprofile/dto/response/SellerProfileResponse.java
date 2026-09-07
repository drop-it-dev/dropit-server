package com.dropit.sellerprofile.dto.response;

import com.dropit.sellerprofile.entity.SellerProfile;

public record SellerProfileResponse(
        Long id,
        Long userId,
        String description,
        String imageUrl,
        String instagramUrl,
        String youtubeUrl
) {

    public static SellerProfileResponse from(SellerProfile sellerProfile) {
        return new SellerProfileResponse(
                sellerProfile.getId(),
                sellerProfile.getUser().getId(),
                sellerProfile.getDescription(),
                sellerProfile.getImageUrl(),
                sellerProfile.getInstagramUrl(),
                sellerProfile.getYoutubeUrl()
        );
    }
}
