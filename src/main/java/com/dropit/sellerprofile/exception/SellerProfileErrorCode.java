package com.dropit.sellerprofile.exception;

import com.dropit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum SellerProfileErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    SELLER_ROLE_REQUIRED(HttpStatus.FORBIDDEN, "판매자만 판매자 프로필을 등록할 수 있습니다."),
    SELLER_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 판매자 프로필입니다."),
    SELLER_PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 판매자 프로필이 존재합니다.");

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
