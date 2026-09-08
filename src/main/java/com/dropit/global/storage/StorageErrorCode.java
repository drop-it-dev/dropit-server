package com.dropit.global.storage;

import com.dropit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum StorageErrorCode implements ErrorCode {

    EMPTY_FILE(
            HttpStatus.BAD_REQUEST,
            "업로드할 이미지 파일이 비어 있습니다."
    ),
    UNSUPPORTED_IMAGE_TYPE(
            HttpStatus.BAD_REQUEST,
            "JPEG, PNG, WEBP 이미지만 업로드할 수 있습니다."
    ),
    FILE_UPLOAD_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "이미지 업로드에 실패했습니다."
    ),
    FILE_URL_CREATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "이미지 URL 생성에 실패했습니다."
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