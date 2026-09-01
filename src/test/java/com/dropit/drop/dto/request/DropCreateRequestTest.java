package com.dropit.drop.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("올바른 원화 단위 드랍 생성 요청은 검증을 통과한다")
    void acceptValidRequest() {
        DropCreateRequest request = createRequest(10, 20, 2);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("구매 제한 수량이 0인 생성 요청은 검증을 통과한다")
    void acceptZeroPurchaseLimitAsUnlimited() {
        DropCreateRequest request = createRequest(10, 20, 0);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("잘못된 수량, 할인율, 구매 제한 수량은 검증에 실패한다")
    void rejectInvalidQuantityDiscountRateAndPurchaseLimit() {
        DropCreateRequest request = createRequest(0, 100, -1);

        Set<ConstraintViolation<DropCreateRequest>> violations = validator.validate(request);

        assertEquals(1, count(violations, "initialQuantity"));
        assertEquals(1, count(violations, "discountRate"));
        assertEquals(1, count(violations, "purchaseLimit"));
    }

    @Test
    @DisplayName("필수 상품, 가격, 판매 기간 정보가 없으면 검증에 실패한다")
    void rejectMissingProductAndPeriod() {
        DropCreateRequest request = new DropCreateRequest(null, null, 10, 20, null, null, null);

        Set<ConstraintViolation<DropCreateRequest>> violations = validator.validate(request);

        assertEquals(1, count(violations, "productId"));
        assertEquals(1, count(violations, "price"));
        assertEquals(1, count(violations, "purchaseLimit"));
        assertEquals(1, count(violations, "openAt"));
        assertEquals(1, count(violations, "closeAt"));
    }

    @Test
    @DisplayName("원화 가격에 소수점이 포함되면 검증에 실패한다")
    void rejectPriceWithFractionalWon() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        DropCreateRequest request = new DropCreateRequest(
                1L,
                new BigDecimal("59000.50"),
                10,
                20,
                2,
                openAt,
                openAt.plusDays(1)
        );

        assertViolationCount(request, "price", 1);
    }

    private DropCreateRequest createRequest(int quantity, int discountRate, Integer purchaseLimit) {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        return new DropCreateRequest(
                1L,
                new BigDecimal("59000"),
                quantity,
                discountRate,
                purchaseLimit,
                openAt,
                openAt.plusDays(1)
        );
    }

    private long count(Set<ConstraintViolation<DropCreateRequest>> violations, String property) {
        return violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().equals(property))
                .count();
    }

    private void assertViolationCount(DropCreateRequest request, String property, long expectedCount) {
        assertEquals(expectedCount, count(validator.validate(request), property));
    }
}
