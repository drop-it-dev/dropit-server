package com.dropit.drop.entity;

import com.dropit.drop.exception.DropErrorCode;
import com.dropit.global.exception.ServiceException;
import com.dropit.product.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DropTest {

    private final Product product = mock(Product.class);

    @Test
    @DisplayName("드랍 상태는 판매 기간과 재고를 기준으로 계산한다")
    void calculateCurrentStatusFromPeriodAndStock() {
        LocalDateTime openAt = LocalDateTime.of(2026, 9, 1, 12, 0);
        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 2, openAt, openAt.plusHours(1));

        assertEquals(DropStatus.READY, drop.currentStatus(openAt.minusSeconds(1)));
        assertEquals(DropStatus.OPEN, drop.currentStatus(openAt));
        assertEquals(DropStatus.CLOSED, drop.currentStatus(openAt.plusHours(1)));
    }

    @Test
    @DisplayName("구매 제한 수량이 0이면 무제한으로 저장된다")
    void acceptZeroPurchaseLimitAsUnlimited() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);

        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 0, openAt, openAt.plusDays(1));

        assertEquals(0, drop.getPurchaseLimit());
    }

    @Test
    @DisplayName("드랍은 생성 시 기본적으로 비공개 상태이다")
    void defaultVisibilityIsFalse() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 0, openAt, openAt.plusDays(1));

        assertEquals(false, drop.isVisible());
    }

    @Test
    @DisplayName("드랍 공개 여부를 변경할 수 있다")
    void changeVisibility() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 0, openAt, openAt.plusDays(1));

        drop.changeVisibility(true);

        assertEquals(true, drop.isVisible());
    }

    @Test
    @DisplayName("판매 시작 시각과 종료 시각이 같으면 드랍을 생성할 수 없다")
    void rejectInvalidDropPeriodWithDropErrorCode() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> new Drop(product, new BigDecimal("59000"), 10, 20, 2, openAt, openAt)
        );

        assertEquals(DropErrorCode.INVALID_DROP_PERIOD, exception.getErrorCode());
    }
}
