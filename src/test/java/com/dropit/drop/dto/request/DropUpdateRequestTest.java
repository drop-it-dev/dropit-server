package com.dropit.drop.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropUpdateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("원화 단위 가격을 포함한 드랍 수정 요청은 검증을 통과한다")
    void acceptValidPrice() {
        DropUpdateRequest request = new DropUpdateRequest(
                new BigDecimal("49000"),
                null,
                null,
                null,
                null,
                null
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("드랍 수정 가격에 소수점이 포함되면 검증에 실패한다")
    void rejectPriceWithFractionalWon() {
        DropUpdateRequest request = new DropUpdateRequest(
                new BigDecimal("49000.50"),
                null,
                null,
                null,
                null,
                null
        );

        Set<ConstraintViolation<DropUpdateRequest>> violations = validator.validate(request);

        long priceViolationCount = violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().equals("price"))
                .count();
        assertEquals(1, priceViolationCount);
    }
}
