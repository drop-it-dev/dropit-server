package com.dropit.drop.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropVisibilityUpdateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("공개 여부가 포함된 변경 요청은 검증을 통과한다")
    void acceptVisibility() {
        DropVisibilityUpdateRequest request = new DropVisibilityUpdateRequest(true);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("공개 여부가 없으면 변경 요청은 검증에 실패한다")
    void rejectMissingVisibility() {
        DropVisibilityUpdateRequest request = new DropVisibilityUpdateRequest(null);

        Set<ConstraintViolation<DropVisibilityUpdateRequest>> violations = validator.validate(request);

        long visibleViolationCount = violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().equals("visible"))
                .count();
        assertEquals(1, visibleViolationCount);
    }
}
