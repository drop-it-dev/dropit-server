package com.dropit.drop.dto.request;

import com.dropit.drop.entity.DropStatus;

public record DropSearchCondition(
        String keyword,
        DropStatus status,
        DropSortType sortType
) {
}
