package com.dropit.product.entity;

import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductTest {

    private final User seller = new User(
            "seller@example.com",
            "encoded-password",
            "seller",
            UserRole.SELLER
    );

    @Test
    @DisplayName("상품을 생성하면 입력한 정보가 저장된다")
    void createProduct() {
        Product product = new Product(
                seller,
                "Limited Hoodie",
                "Limited edition hoodie",
                "products/limited-hoodie.webp"
        );

        assertEquals(seller, product.getSeller());
        assertEquals("Limited Hoodie", product.getName());
        assertEquals("Limited edition hoodie", product.getDescription());
        assertEquals("products/limited-hoodie.webp", product.getImageUrl());
    }

    @Test
    @DisplayName("상품의 기본 정보를 수정할 수 있다")
    void updateInfo() {
        Product product = createProductWithoutImage();

        product.updateInfo("Updated Product", "Updated description");

        assertEquals("Updated Product", product.getName());
        assertEquals("Updated description", product.getDescription());
    }

    @Test
    @DisplayName("대표 이미지를 변경할 수 있다")
    void changeImage() {
        Product product = createProductWithoutImage();

        product.changeImage("products/new-image.webp");

        assertEquals("products/new-image.webp", product.getImageUrl());
    }

    private Product createProductWithoutImage() {
        return new Product(
                seller,
                "Product",
                "Description",
                null
        );
    }
}
