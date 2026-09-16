package com.dropit.drop.cache;

import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.entity.Drop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record DropListCacheValue(
        List<DropListCacheItem> content,
        long totalElements
) {

    public static DropListCacheValue from(Page<Drop> drops) {
        List<DropListCacheItem> content = drops.getContent().stream()
                .map(DropListCacheItem::from)
                .toList();

        return new DropListCacheValue(content, drops.getTotalElements());
    }

    public List<Long> dropIds() {
        return content.stream()
                .map(DropListCacheItem::id)
                .toList();
    }

    public Page<DropResponse> toPage(
            Pageable pageable,
            Map<Long, Integer> remainingQuantities,
            LocalDateTime now
    ) {
        List<DropResponse> responses = content.stream()
                .map(item -> item.toResponse(
                        requireRemainingQuantity(item.id(), remainingQuantities),
                        now
                ))
                .toList();

        return new PageImpl<>(responses, pageable, totalElements);
    }

    private int requireRemainingQuantity(
            Long dropId,
            Map<Long, Integer> remainingQuantities
    ) {
        Integer remainingQuantity = remainingQuantities.get(dropId);
        if (remainingQuantity == null) {
            throw new IllegalStateException("드랍 재고를 조회할 수 없습니다. dropId=" + dropId);
        }
        return remainingQuantity;
    }
}
