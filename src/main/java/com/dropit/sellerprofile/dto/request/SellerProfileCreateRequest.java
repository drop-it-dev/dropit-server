package com.dropit.sellerprofile.dto.request;

import jakarta.validation.constraints.Size;

public record SellerProfileCreateRequest(
        @Size(max = 2000, message = "판매자 소개는 2000자 이하여야 합니다.")
        String description,

        @Size(max = 2048, message = "이미지 URL은 2048자 이하여야 합니다.")
        String imageUrl,

        @Size(max = 2048, message = "인스타그램 URL은 2048자 이하여야 합니다.")
        String instagramUrl,

        @Size(max = 2048, message = "유튜브 URL은 2048자 이하여야 합니다.")
        String youtubeUrl
) {
}
