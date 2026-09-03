package com.dropit.product.entity;

import com.dropit.global.entity.BaseEntity;
import com.dropit.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    public Product(User seller, String name, String description, String imageUrl) {
        this.seller = seller;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public void updateInfo(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void changeImage(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
