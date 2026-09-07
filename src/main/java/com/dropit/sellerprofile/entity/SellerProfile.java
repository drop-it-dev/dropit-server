package com.dropit.sellerprofile.entity;

import com.dropit.global.entity.BaseEntity;
import com.dropit.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "seller_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "instagram_url", length = 2048)
    private String instagramUrl;

    @Column(name = "youtube_url", length = 2048)
    private String youtubeUrl;

    public SellerProfile(
            User user,
            String description,
            String imageUrl,
            String instagramUrl,
            String youtubeUrl
    ) {
        this.user = user;
        this.description = description;
        this.imageUrl = imageUrl;
        this.instagramUrl = instagramUrl;
        this.youtubeUrl = youtubeUrl;
    }

    public void update(
            String description,
            String imageUrl,
            String instagramUrl,
            String youtubeUrl
    ) {
        this.description = description;
        this.imageUrl = imageUrl;
        this.instagramUrl = instagramUrl;
        this.youtubeUrl = youtubeUrl;
    }
}
