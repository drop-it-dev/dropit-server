package com.dropit.product.exception;

import com.dropit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 판매자입니다."),
    SELLER_ROLE_REQUIRED(HttpStatus.FORBIDDEN, "판매자만 상품을 등록할 수 있습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다."),
    PRODUCT_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "상품 소유자만 변경할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String message() {
        return message;
    }
}
