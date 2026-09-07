package com.dropit.sellerprofile.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellerProfileCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("올바른 판매자 프로필 요청은 검증을 통과한다")
    void acceptValidRequest() {
        SellerProfileCreateRequest request = new SellerProfileCreateRequest(
                "판매자 소개",
                "https://example.com/profile.png",
                "https://instagram.com/example",
                "https://youtube.com/@example"
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("소개는 2000자를 초과할 수 없다")
    void rejectTooLongDescription() {
        SellerProfileCreateRequest request = new SellerProfileCreateRequest(
                "a".repeat(2001), null, null, null
        );

        assertViolationCount(request, "description", 1);
    }

    @Test
    @DisplayName("URL은 2048자를 초과할 수 없다")
    void rejectTooLongUrl() {
        SellerProfileCreateRequest request = new SellerProfileCreateRequest(
                null, "a".repeat(2049), null, null
        );

        assertViolationCount(request, "imageUrl", 1);
    }

    private void assertViolationCount(
            SellerProfileCreateRequest request,
            String propertyName,
            long expectedCount
    ) {
        Set<ConstraintViolation<SellerProfileCreateRequest>> violations = validator.validate(request);

        long actualCount = violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().equals(propertyName))
                .count();

        assertEquals(expectedCount, actualCount);
    }
}
