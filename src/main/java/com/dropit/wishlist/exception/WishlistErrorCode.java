package com.dropit.wishlist.exception;


import com.dropit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum WishlistErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다."),
    WISHLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 찜입니다."),
    WISHLIST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 찜한 상품입니다.");

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
