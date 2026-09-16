package com.dropit.drop.service;

import com.dropit.drop.cache.DropDetailCacheReader;
import com.dropit.drop.cache.DropDetailCacheValue;
import com.dropit.drop.cache.DropListCacheLoader;
import com.dropit.drop.cache.DropListCacheMetrics;
import com.dropit.drop.cache.DropListCacheReader;
import com.dropit.drop.cache.DropListCacheValue;
import com.dropit.drop.cache.DropListLocalFallback;
import com.dropit.drop.cache.DropListLocalReadCache;
import com.dropit.drop.cache.DropListStockReader;
import com.dropit.drop.dto.request.DropCreateRequest;
import com.dropit.drop.dto.request.DropSearchCondition;
import com.dropit.drop.dto.request.DropSortType;
import com.dropit.drop.dto.request.DropUpdateRequest;
import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.exception.DropErrorCode;
import com.dropit.drop.repository.DropRepository;
import com.dropit.drop.repository.DropLiveState;
import com.dropit.global.config.RedisCacheConfig;
import com.dropit.global.exception.ServiceException;
import com.dropit.product.entity.Product;
import com.dropit.product.exception.ProductErrorCode;
import com.dropit.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DropService {

    private final DropRepository dropRepository;
    private final ProductRepository productRepository;
    private final DropDetailCacheReader dropDetailCacheReader;
    private final DropListCacheReader dropListCacheReader;
    private final DropListCacheLoader dropListCacheLoader;
    private final DropListStockReader dropListStockReader;
    private final DropListCacheMetrics dropListCacheMetrics;
    private final DropListLocalFallback dropListLocalFallback;
    private final DropListLocalReadCache dropListLocalReadCache;

    @Value("${app.drop.list.redis-cache.enabled:true}")
    private boolean listRedisCacheEnabled = true;

    @Value("${app.drop.list.local-cache.enabled:true}")
    private boolean listLocalCacheEnabled = true;

    @Transactional
    @CacheEvict(
            cacheNames = RedisCacheConfig.DROP_LIST_CACHE,
            allEntries = true,
            beforeInvocation = true
    )
    public Long save(Long userId, DropCreateRequest request) {
        Product product = productRepository.findByIdForUpdate(request.productId())
                .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!Objects.equals(product.getSeller().getId(), userId)) {
            throw new ServiceException(ProductErrorCode.PRODUCT_OWNER_REQUIRED);
        }

        Drop drop = new Drop(
                product,
                request.price(),
                request.initialQuantity(),
                request.discountRate(),
                request.purchaseLimit(),
                request.openAt(),
                request.closeAt()
        );

        Drop savedDrop = dropRepository.save(drop);

        dropListLocalReadCache.invalidate();

        return savedDrop.getId();
    }

    public Page<DropResponse> getAll(
            DropSearchCondition condition,
            Pageable pageable
    ) {
        if (!supportsListCache(condition, pageable)) {
            return dropListCacheLoader.search(condition, pageable);
        }

        dropListCacheMetrics.recordRequest();
        DropListCacheValue cachedPage = listLocalCacheEnabled
                ? dropListLocalReadCache.get(dropListCacheReader::getLatestFirstPage)
                : dropListCacheReader.getLatestFirstPage();
        dropListLocalFallback.remember(cachedPage);
        Map<Long, Integer> remainingQuantities = dropListStockReader
                .getRemainingQuantities(cachedPage.dropIds());

        return cachedPage.toPage(
                pageable,
                remainingQuantities,
                LocalDateTime.now()
        );
    }

    private boolean supportsListCache(
            DropSearchCondition condition,
            Pageable pageable
    ) {
        boolean hasNoKeyword = condition.keyword() == null
                || condition.keyword().isBlank();
        boolean usesLatestSort = condition.sortType() == null
                || condition.sortType() == DropSortType.LATEST;

        return listRedisCacheEnabled
                && hasNoKeyword
                && condition.status() == null
                && usesLatestSort
                && pageable.getPageNumber() == 0
                && pageable.getPageSize() == 20
                && pageable.getSort().isUnsorted();
    }

    public DropResponse getOne(Long dropId) {
        DropDetailCacheValue detail = dropDetailCacheReader.get(dropId);
        DropLiveState liveState = dropRepository.findLiveStateById(dropId)
                .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));

        if (!liveState.isVisible()) {
            throw new ServiceException(DropErrorCode.DROP_NOT_FOUND);
        }

        return DropResponse.from(
                detail,
                liveState.getRemainingQuantity(),
                liveState.isVisible(),
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public Page<DropResponse> getPublicDropsBySeller(
            Long sellerId,
            Pageable pageable
    ) {
        Page<Drop> drops = dropRepository.findAllByProductSellerIdAndVisibleTrue(
                sellerId,
                pageable
        );

        return drops.map(DropResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<DropResponse> getDropsForSellerManagement(
            Long sellerId,
            Pageable pageable
    ) {
        Page<Drop> drops = dropRepository.findAllByProductSellerId(
                sellerId,
                pageable
        );

        return drops.map(DropResponse::from);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheConfig.DROP_DETAIL_CACHE, key = "#dropId"),
            @CacheEvict(
                    cacheNames = RedisCacheConfig.DROP_LIST_CACHE,
                    allEntries = true,
                    beforeInvocation = true
            )
    })
    public DropResponse update(Long sellerId, Long dropId, DropUpdateRequest request) {
        Drop drop = dropRepository.findById(dropId)
                .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));

        if (!Objects.equals(drop.getProduct().getSeller().getId(), sellerId)) {
            throw new ServiceException(DropErrorCode.DROP_OWNER_REQUIRED);
        }

        drop.ensureEditable(LocalDateTime.now());

        drop.update(
                request.price(),
                request.initialQuantity(),
                request.discountRate(),
                request.purchaseLimit(),
                request.openAt(),
                request.closeAt()
        );

        dropListLocalReadCache.invalidate();

        return DropResponse.from(drop);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheConfig.DROP_DETAIL_CACHE, key = "#dropId"),
            @CacheEvict(
                    cacheNames = RedisCacheConfig.DROP_LIST_CACHE,
                    allEntries = true,
                    beforeInvocation = true
            )
    })
    public void delete(Long sellerId, Long dropId) {
        Drop drop = dropRepository.findById(dropId)
                .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));

        if (!Objects.equals(drop.getProduct().getSeller().getId(), sellerId)) {
            throw new ServiceException(DropErrorCode.DROP_OWNER_REQUIRED);
        }

        drop.ensureEditable(LocalDateTime.now());

        dropRepository.delete(drop);
        dropListLocalReadCache.invalidate();
    }
}
