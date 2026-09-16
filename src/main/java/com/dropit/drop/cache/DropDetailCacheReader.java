package com.dropit.drop.cache;

import com.dropit.drop.entity.Drop;
import com.dropit.drop.exception.DropErrorCode;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.config.RedisCacheConfig;
import com.dropit.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DropDetailCacheReader {

    private final DropRepository dropRepository;

    @Cacheable(cacheNames = RedisCacheConfig.DROP_DETAIL_CACHE, key = "#dropId", sync = true)
    public DropDetailCacheValue get(Long dropId) {
        Drop drop = dropRepository.findDetailById(dropId)
                .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));

        if (!drop.isVisible()) {
            throw new ServiceException(DropErrorCode.DROP_NOT_FOUND);
        }

        return DropDetailCacheValue.from(drop);
    }
}
