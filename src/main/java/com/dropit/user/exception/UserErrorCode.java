package com.dropit.user.exception;

import com.dropit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 사용자입니다."
    ),

    EMAIL_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "이미 사용 중인 이메일입니다."
    ),

    USERNAME_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "이미 사용 중인 사용자명입니다."
    ),

    INVALID_CURRENT_PASSWORD(
            HttpStatus.BAD_REQUEST,
            "현재 비밀번호가 올바르지 않습니다."
    );

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