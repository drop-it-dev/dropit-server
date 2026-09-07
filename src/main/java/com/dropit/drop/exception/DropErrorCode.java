package com.dropit.drop.exception;

import com.dropit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum DropErrorCode implements ErrorCode {

    DROP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 드랍입니다."),
    DROP_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "해당 드랍은 판매자만 관리할 수 있습니다."),
    DROP_NOT_OPEN(HttpStatus.CONFLICT, "현재 주문할 수 없는 드랍입니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "드랍 재고가 부족합니다."),
    INVALID_DROP_PERIOD(HttpStatus.BAD_REQUEST, "판매 종료 시각은 시작 시각보다 늦어야 합니다."),
    DROP_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 판매가 시작된 드랍입니다.");

    private final HttpStatus status;
    private final String message;

    @Override public HttpStatus status() { return status; }
    @Override public String message() { return message; }
}
