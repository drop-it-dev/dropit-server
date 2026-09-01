package com.dropit.product.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("올바른 상품 등록 요청은 검증을 통과한다")
    void acceptValidRequest() {
        ProductCreateRequest request = new ProductCreateRequest(
                "Limited Hoodie",
                new BigDecimal("59000.00"),
                "Limited edition hoodie"
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("상품명은 공백일 수 없다")
    void rejectBlankName() {
        ProductCreateRequest request = new ProductCreateRequest(
                "   ",
                new BigDecimal("59000.00"),
                null
        );

        assertViolationCount(request, "name", 1);
    }

    @Test
    @DisplayName("상품명은 100자를 초과할 수 없다")
    void rejectTooLongName() {
        ProductCreateRequest request = new ProductCreateRequest(
                "a".repeat(101),
                new BigDecimal("59000.00"),
                null
        );

        assertViolationCount(request, "name", 1);
    }

    @Test
    @DisplayName("가격은 필수이며 0보다 커야 한다")
    void rejectMissingOrNonPositivePrice() {
        ProductCreateRequest missingPrice = new ProductCreateRequest("Product", null, null);
        ProductCreateRequest zeroPrice = new ProductCreateRequest("Product", BigDecimal.ZERO, null);

        assertViolationCount(missingPrice, "price", 1);
        assertViolationCount(zeroPrice, "price", 1);
    }

    @Test
    @DisplayName("가격은 DB 컬럼 범위인 정수 13자리와 소수 2자리를 넘을 수 없다")
    void rejectPriceOutsideColumnRange() {
        ProductCreateRequest tooManyIntegerDigits = new ProductCreateRequest(
                "Product",
                new BigDecimal("12345678901234.00"),
                null
        );
        ProductCreateRequest tooManyFractionDigits = new ProductCreateRequest(
                "Product",
                new BigDecimal("1000.001"),
                null
        );

        assertViolationCount(tooManyIntegerDigits, "price", 1);
        assertViolationCount(tooManyFractionDigits, "price", 1);
    }

    private void assertViolationCount(
            ProductCreateRequest request,
            String propertyName,
            long expectedCount
    ) {
        Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

        long actualCount = violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().equals(propertyName))
                .count();

        assertEquals(expectedCount, actualCount);
    }
}
