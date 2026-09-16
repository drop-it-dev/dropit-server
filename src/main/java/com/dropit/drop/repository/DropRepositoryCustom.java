package com.dropit.drop.repository;

import com.dropit.drop.dto.request.DropSearchCondition;
import com.dropit.drop.entity.Drop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface DropRepositoryCustom {

    Page<Drop> searchPublicDrops(
            DropSearchCondition condition,
            Pageable pageable
    );

    Map<Long, Integer> findRemainingQuantitiesByIds(List<Long> dropIds);
}
