package com.dropit.ranking.exception;

import com.dropit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum RankingErrorCode implements ErrorCode {

    RANKING_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE, "아직 공개된 랭킹이 없습니다."),
    RANKING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "랭킹 조회 데이터가 유실되었거나 일관성이 없습니다.");

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
