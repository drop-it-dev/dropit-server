package com.dropit.drop.cache;

import com.dropit.drop.dto.request.DropSearchCondition;
import com.dropit.drop.dto.request.DropSortType;
import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.repository.DropRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DropListCacheLoader {

    private static final int FIRST_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final DropRepository dropRepository;

    @Transactional(readOnly = true)
    public DropListCacheValue loadLatestFirstPage() {
        DropSearchCondition condition = new DropSearchCondition(
                null,
                null,
                DropSortType.LATEST
        );
        Page<Drop> drops = dropRepository.searchPublicDrops(
                condition,
                PageRequest.of(FIRST_PAGE, DEFAULT_PAGE_SIZE)
        );

        return DropListCacheValue.from(drops);
    }

    @Transactional(readOnly = true)
    public Page<DropResponse> search(
            DropSearchCondition condition,
            Pageable pageable
    ) {
        return dropRepository.searchPublicDrops(condition, pageable)
                .map(DropResponse::from);
    }
}
