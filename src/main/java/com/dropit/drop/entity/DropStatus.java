package com.dropit.drop.entity;

import java.time.LocalDateTime;

public enum DropStatus {
    READY,
    OPEN,
    SOLDOUT,
    CLOSED;

    public static DropStatus resolve(
            LocalDateTime openAt,
            LocalDateTime closeAt,
            int remainingQuantity,
            LocalDateTime now
    ) {
        if (!now.isBefore(closeAt)) return CLOSED;
        if (now.isBefore(openAt)) return READY;
        if (remainingQuantity == 0) return SOLDOUT;
        return OPEN;
    }
}
