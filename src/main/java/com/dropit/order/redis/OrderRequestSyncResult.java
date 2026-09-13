package com.dropit.order.redis;

public enum OrderRequestSyncResult {
    APPLIED,
    ALREADY_APPLIED,
    MISSING,
    INVALID
}
