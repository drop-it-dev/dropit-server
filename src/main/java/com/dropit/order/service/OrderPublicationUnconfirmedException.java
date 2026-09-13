package com.dropit.order.service;

import java.util.UUID;

public class OrderPublicationUnconfirmedException extends RuntimeException {

    private final UUID requestId;

    public OrderPublicationUnconfirmedException(UUID requestId, Throwable cause) {
        super("주문 메시지 발행 결과를 확인할 수 없습니다.", cause);
        this.requestId = requestId;
    }

    public UUID getRequestId() {
        return requestId;
    }
}
