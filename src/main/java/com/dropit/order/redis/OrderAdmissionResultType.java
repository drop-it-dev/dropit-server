package com.dropit.order.redis;

public enum OrderAdmissionResultType {
    NEW,
    REPLAY,
    CONFLICT,
    OUT_OF_STOCK,
    PURCHASE_LIMIT_EXCEEDED,
    NOT_READY
}
