package com.dropit.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    HttpStatus status();

    String message();

    default String code() {
        return ((Enum<?>) this).name();
    }
}
