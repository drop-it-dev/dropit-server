package com.dropit.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> errors
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.code(), errorCode.message(), null);
    }

    public static ErrorResponse from(ErrorCode errorCode, Map<String, String> errors) {
        return new ErrorResponse(errorCode.code(), errorCode.message(), errors);
    }
}