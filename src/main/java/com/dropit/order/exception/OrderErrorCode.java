package com.dropit.order.exception;

import com.dropit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 주문입니다."),
    ORDER_ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 주문입니다."),
    PURCHASE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "드랍의 1인당 구매 제한 수량을 초과했습니다.");

    private final HttpStatus status;
    private final String message;

    @Override public HttpStatus status() { return status; }
    @Override public String message() { return message; }
}
