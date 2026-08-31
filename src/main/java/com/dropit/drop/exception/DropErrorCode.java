package com.dropit.drop.exception;

import com.dropit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum DropErrorCode implements ErrorCode {

    DROP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 드랍입니다."),

    private final HttpStatus status;
    private final String message;

    @Override public HttpStatus status() { return status; }
    @Override public String message() { return message; }
}
