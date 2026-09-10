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
import static org.junit.jupiter.api.Assertions.assertAll;
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
                () -> assertThrows(ServiceException.class,
                        () -> new Drop(product, BigDecimal.ZERO, 10, 20, 0, openAt, openAt.plusDays(1))),
                () -> assertThrows(ServiceException.class,
                        () -> new Drop(product, new BigDecimal("59000"), 0, 20, 0, openAt, openAt.plusDays(1))),
                () -> assertThrows(ServiceException.class,
                        () -> new Drop(product, new BigDecimal("59000"), 10, 101, 0, openAt, openAt.plusDays(1))),
                () -> assertThrows(ServiceException.class,
                        () -> new Drop(product, new BigDecimal("59000"), 10, 20, -1, openAt, openAt.plusDays(1))),
                () -> assertThrows(ServiceException.class,
                        () -> new Drop(product, new BigDecimal("59000.50"), 10, 20, 0, openAt, openAt.plusDays(1))),
                () -> assertThrows(ServiceException.class,
                        () -> new Drop(product, new BigDecimal("10000000000000"), 10, 20, 0, openAt, openAt.plusDays(1)))
        );
    }

    @Test
    @DisplayName("드랍 수정은 최종 값 전체를 검증하고 실패 시 기존 값을 보존한다")
    void preserveAllValuesWhenUpdateIsInvalid() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        LocalDateTime closeAt = openAt.plusDays(1);
        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 2, openAt, closeAt);

        assertThrows(
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

        assertAll(
                () -> assertEquals(new BigDecimal("59000"), drop.getPrice()),
                () -> assertEquals(10, drop.getInitialQuantity()),
                () -> assertEquals(10, drop.getRemainingQuantity()),
                () -> assertEquals(20, drop.getDiscountRate()),
                () -> assertEquals(2, drop.getPurchaseLimit()),
                () -> assertEquals(openAt, drop.getOpenAt()),
                () -> assertEquals(closeAt, drop.getCloseAt())
        );
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
