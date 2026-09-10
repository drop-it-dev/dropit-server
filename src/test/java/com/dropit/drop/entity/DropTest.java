package com.dropit.drop.entity;

import com.dropit.drop.exception.DropErrorCode;
import com.dropit.global.exception.ServiceException;
import com.dropit.product.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
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
    @DisplayName("드랍 생성 시 숫자 판매 정책의 유효 범위를 검증한다")
    void rejectInvalidSalesValuesOnCreate() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);

        assertAll(
                () -> assertInvalidSalesValue(() -> new Drop(product, null, 10, 20, 0, openAt, openAt.plusDays(1))),
                () -> assertInvalidSalesValue(() -> new Drop(product, new BigDecimal("-1"), 10, 20, 0, openAt, openAt.plusDays(1))),
                () -> assertInvalidSalesValue(() -> new Drop(product, BigDecimal.ZERO, 10, 20, 0, openAt, openAt.plusDays(1))),
                () -> assertInvalidSalesValue(() -> new Drop(product, new BigDecimal("59000"), 0, 20, 0, openAt, openAt.plusDays(1))),
                () -> assertInvalidSalesValue(() -> new Drop(product, new BigDecimal("59000"), 10, -1, 0, openAt, openAt.plusDays(1))),
                () -> assertInvalidSalesValue(() -> new Drop(product, new BigDecimal("59000"), 10, 101, 0, openAt, openAt.plusDays(1))),
                () -> assertInvalidSalesValue(() -> new Drop(product, new BigDecimal("59000"), 10, 20, -1, openAt, openAt.plusDays(1))),
                () -> assertInvalidSalesValue(() -> new Drop(product, new BigDecimal("59000.50"), 10, 20, 0, openAt, openAt.plusDays(1))),
                () -> assertInvalidSalesValue(() -> new Drop(product, new BigDecimal("10000000000000"), 10, 20, 0, openAt, openAt.plusDays(1)))
        );
    }

    @Test
    @DisplayName("할인율 0과 100, 최대 가격은 유효한 판매 값으로 허용한다")
    void acceptSalesValueBoundariesOnCreate() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);

        Drop noDiscount = new Drop(product, new BigDecimal("9999999999999"), 10, 0, 0, openAt, openAt.plusDays(1));
        Drop fullDiscount = new Drop(product, new BigDecimal("59000"), 10, 100, 0, openAt, openAt.plusDays(1));

        assertAll(
                () -> assertEquals(new BigDecimal("9999999999999"), noDiscount.getPrice()),
                () -> assertEquals(0, noDiscount.getDiscountRate()),
                () -> assertEquals(100, fullDiscount.getDiscountRate())
        );
    }

    @Test
    @DisplayName("드랍 수정은 최종 값 전체를 검증하고 실패 시 기존 값을 보존한다")
    void preserveAllValuesWhenUpdateIsInvalid() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        LocalDateTime closeAt = openAt.plusDays(1);
        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 2, openAt, closeAt);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> drop.update(
                        new BigDecimal("49000"),
                        20,
                        101,
                        3,
                        openAt,
                        closeAt
                )
        );

        assertEquals(DropErrorCode.INVALID_DROP_VALUE, exception.getErrorCode());
        assertDropValues(drop, new BigDecimal("59000"), 10, 10, 20, 2, openAt, closeAt);
    }

    @Test
    @DisplayName("모든 값이 null인 부분 수정은 기존 값을 유지한다")
    void preserveAllValuesWhenUpdateIsEmpty() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        LocalDateTime closeAt = openAt.plusDays(1);
        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 2, openAt, closeAt);

        drop.update(null, null, null, null, null, null);

        assertDropValues(drop, new BigDecimal("59000"), 10, 10, 20, 2, openAt, closeAt);
    }

    @Test
    @DisplayName("부분 수정으로 판매 기간이 역전되면 INVALID_DROP_PERIOD를 반환하고 기존 기간을 보존한다")
    void rejectReversedPeriodOnPartialUpdate() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        LocalDateTime closeAt = openAt.plusDays(1);
        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 2, openAt, closeAt);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> drop.update(null, null, null, null, closeAt.plusDays(1), null)
        );

        assertEquals(DropErrorCode.INVALID_DROP_PERIOD, exception.getErrorCode());
        assertEquals(openAt, drop.getOpenAt());
        assertEquals(closeAt, drop.getCloseAt());
    }

    @Test
    @DisplayName("재고 차감 수량은 양수만 허용한다")
    void rejectNonPositiveDecreaseQuantity() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 12, 0);
        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 0, now.minusHours(1), now.plusHours(1));
        drop.changeVisibility(true);

        assertThrows(ServiceException.class, () -> drop.decreaseStock(0, now));
        assertThrows(ServiceException.class, () -> drop.decreaseStock(-1, now));
        assertEquals(10, drop.getRemainingQuantity());
    }

    @Test
    @DisplayName("드랍은 생성 시 기본적으로 비공개 상태이다")
    void defaultVisibilityIsFalse() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 0, openAt, openAt.plusDays(1));

        assertFalse(drop.isVisible());
    }

    @Test
    @DisplayName("드랍 공개 여부를 변경할 수 있다")
    void changeVisibility() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 0, openAt, openAt.plusDays(1));

        drop.changeVisibility(true);

        assertTrue(drop.isVisible());
    }

    @Test
    @DisplayName("판매 시작 시각과 종료 시각이 같으면 드랍을 생성할 수 없다")
    void rejectInvalidDropPeriodWithDropErrorCode() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);

        assertAll(
                () -> assertInvalidDropPeriod(() -> new Drop(product, new BigDecimal("59000"), 10, 20, 2, openAt, openAt)),
                () -> assertInvalidDropPeriod(() -> new Drop(product, new BigDecimal("59000"), 10, 20, 2, openAt.plusDays(1), openAt)),
                () -> assertInvalidDropPeriod(() -> new Drop(product, new BigDecimal("59000"), 10, 20, 2, null, openAt)),
                () -> assertInvalidDropPeriod(() -> new Drop(product, new BigDecimal("59000"), 10, 20, 2, openAt, null))
        );
    }

    private void assertInvalidSalesValue(Executable executable) {
        ServiceException exception = assertThrows(ServiceException.class, executable);
        assertEquals(DropErrorCode.INVALID_DROP_VALUE, exception.getErrorCode());
    }

    private void assertInvalidDropPeriod(Executable executable) {
        ServiceException exception = assertThrows(ServiceException.class, executable);
        assertEquals(DropErrorCode.INVALID_DROP_PERIOD, exception.getErrorCode());
    }

    private void assertDropValues(
            Drop drop,
            BigDecimal price,
            int initialQuantity,
            int remainingQuantity,
            int discountRate,
            int purchaseLimit,
            LocalDateTime openAt,
            LocalDateTime closeAt
    ) {
        assertAll(
                () -> assertEquals(price, drop.getPrice()),
                () -> assertEquals(initialQuantity, drop.getInitialQuantity()),
                () -> assertEquals(remainingQuantity, drop.getRemainingQuantity()),
                () -> assertEquals(discountRate, drop.getDiscountRate()),
                () -> assertEquals(purchaseLimit, drop.getPurchaseLimit()),
                () -> assertEquals(openAt, drop.getOpenAt()),
                () -> assertEquals(closeAt, drop.getCloseAt())
        );
    }

    @Test
    @DisplayName("판매 중인 공개 드랍의 재고를 차감하고 취소 시 복구할 수 있다")
    void decreaseAndRestoreStock() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 12, 0);
        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 0, now.minusHours(1), now.plusHours(1));
        drop.changeVisibility(true);

        drop.decreaseStock(2, now);
        assertEquals(8, drop.getRemainingQuantity());

        drop.restoreStock(2);
        assertEquals(10, drop.getRemainingQuantity());
    }

    @Test
    @DisplayName("드랍 재고보다 많은 수량을 주문할 수 없다")
    void rejectQuantityExceedingStock() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 12, 0);
        Drop drop = new Drop(product, new BigDecimal("59000"), 1, 20, 0, now.minusHours(1), now.plusHours(1));
        drop.changeVisibility(true);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> drop.decreaseStock(2, now)
        );

        assertEquals(DropErrorCode.INSUFFICIENT_STOCK, exception.getErrorCode());
        assertEquals(1, drop.getRemainingQuantity());
    }

    @Test
    @DisplayName("비공개 드랍은 판매 기간이어도 주문할 수 없다")
    void rejectHiddenDropPurchase() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 12, 0);
        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 0, now.minusHours(1), now.plusHours(1));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> drop.decreaseStock(1, now)
        );

        assertEquals(DropErrorCode.DROP_NOT_OPEN, exception.getErrorCode());
    }
}
