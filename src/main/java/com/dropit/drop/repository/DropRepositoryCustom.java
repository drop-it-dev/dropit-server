package com.dropit.drop.repository;

import com.dropit.drop.dto.request.DropSearchCondition;
import com.dropit.drop.entity.Drop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DropRepositoryCustom {

    Page<Drop> searchPublicDrops(
            DropSearchCondition condition,
            Pageable pageable
    );
}
