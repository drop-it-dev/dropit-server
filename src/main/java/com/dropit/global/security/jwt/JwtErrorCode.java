package com.dropit.global.security.jwt;

import com.dropit.global.exception.ErrorCode;
import com.dropit.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum JwtErrorCode implements ErrorCode {

    JWT_BEARER_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "Authorization 헤더에 Bearer 토큰이 필요합니다"),
    JWT_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다"),
    JWT_TOKEN_SIGNATURE_ERROR(HttpStatus.UNAUTHORIZED, "토큰 서명이 일치하지 않습니다"),
    JWT_TOKEN_ERROR(HttpStatus.UNAUTHORIZED, "토큰이 올바르지 않습니다"),
    JWT_TOKEN_UNSUPPORTED_ERROR(HttpStatus.UNAUTHORIZED, "지원하지 않는 토큰입니다"),
    JWT_UNKNOWN_EXCEPTION(HttpStatus.UNAUTHORIZED, "JWT 처리 중 알 수 없는 오류가 발생했습니다"),
    JWT_INVALID_TOKEN_TYPE(HttpStatus.UNAUTHORIZED, "요청에 맞는 토큰 타입이 아닙니다"),
    JWT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다");

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

    public ServiceException toException() {
        return new ServiceException(this);
    }
}

