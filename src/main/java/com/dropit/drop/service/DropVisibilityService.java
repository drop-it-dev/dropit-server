package com.dropit.drop.service;

import com.dropit.drop.cache.DropSaleCacheWriter;
import com.dropit.drop.cache.DropSaleSnapshot;
import com.dropit.drop.dto.request.DropVisibilityUpdateRequest;
import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.exception.DropErrorCode;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.exception.ServiceException;
import com.dropit.order.repository.DropUserPurchaseRepository;
import com.dropit.order.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DropVisibilityService {

    private final DropRepository dropRepository;
    private final OrderItemRepository orderItemRepository;
    private final DropUserPurchaseRepository purchaseRepository;
    private final DropSaleCacheWriter cacheWriter;
    private final TransactionTemplate transactionTemplate;

    public DropResponse changeVisibility(Long sellerId, Long dropId, DropVisibilityUpdateRequest request) {
        ChangeResult result = transactionTemplate.execute(status -> changeInTransaction(sellerId, dropId, request));
        if (result == null) {
            throw new ServiceException(DropErrorCode.DROP_ADMISSION_NOT_READY);
        }
        try {
            cacheWriter.apply(result.snapshot());
        } catch (RuntimeException exception) {
            throw new ServiceException(DropErrorCode.DROP_ADMISSION_NOT_READY, exception);
        }
        return result.response();
    }

    private ChangeResult changeInTransaction(Long sellerId, Long dropId, DropVisibilityUpdateRequest request) {
        Drop drop = dropRepository.findByIdForUpdate(dropId)
                .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));
        if (!Objects.equals(drop.getProduct().getSeller().getId(), sellerId)) {
            throw new ServiceException(DropErrorCode.DROP_OWNER_REQUIRED);
        }
        if (request.visible() && !drop.isSalePrepared()) {
            drop.ensureEditable(LocalDateTime.now());
            if (orderItemRepository.existsByDropId(dropId) || purchaseRepository.existsByDropId(dropId)) {
                throw new ServiceException(DropErrorCode.DROP_SALE_PREPARED);
            }
            drop.prepareSale();
        }
        drop.changeVisibility(request.visible());
        return new ChangeResult(DropResponse.from(drop), DropSaleSnapshot.from(drop));
    }

    private record ChangeResult(DropResponse response, DropSaleSnapshot snapshot) {
    }
}
