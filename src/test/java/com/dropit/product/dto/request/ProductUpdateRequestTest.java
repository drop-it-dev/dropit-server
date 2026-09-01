package com.dropit.product.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductUpdateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("올바른 상품 수정 요청은 검증을 통과한다")
    void acceptValidRequest() {
        ProductUpdateRequest request = new ProductUpdateRequest(
                "Updated Product",
                "Updated description"
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("상품 수정 시 상품명은 공백일 수 없다")
    void rejectBlankName() {
        ProductUpdateRequest request = new ProductUpdateRequest(
                "   ",
                null
        );

        assertViolationCount(request, "name", 1);
    }

    @Test
    @DisplayName("상품 수정 시 상품 설명은 3000자를 초과할 수 없다")
    void rejectTooLongDescription() {
        ProductUpdateRequest request = new ProductUpdateRequest(
                "Product",
                "a".repeat(3001)
        );

        assertViolationCount(request, "description", 1);
    }

    private void assertViolationCount(
            ProductUpdateRequest request,
            String propertyName,
            long expectedCount
    ) {
        Set<ConstraintViolation<ProductUpdateRequest>> violations = validator.validate(request);

        long actualCount = violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().equals(propertyName))
                .count();

        assertEquals(expectedCount, actualCount);
    }
}
