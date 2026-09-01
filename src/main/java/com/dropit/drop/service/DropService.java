package com.dropit.drop.service;

import com.dropit.drop.dto.request.DropCreateRequest;
import com.dropit.drop.dto.request.DropUpdateRequest;
import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.exception.DropErrorCode;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.exception.ServiceException;
import com.dropit.product.entity.Product;
import com.dropit.product.exception.ProductErrorCode;
import com.dropit.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DropService {

    private final DropRepository dropRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Long save(Long userId, DropCreateRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!Objects.equals(product.getSeller().getId(), userId)) {
            throw new ServiceException(ProductErrorCode.PRODUCT_OWNER_REQUIRED);
        }

        Drop drop = new Drop(product, request.initialQuantity(), request.discountRate(), request.purchaseLimit(), request.openAt(), request.closeAt());

        Drop savedDrop = dropRepository.save(drop);

        return savedDrop.getId();
    }

    @Transactional(readOnly = true)
    public List<DropResponse> getAll() {
        List<Drop> drops = dropRepository.findAll();

        return drops.stream().map(DropResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DropResponse getOne(Long dropId) {
        Drop drop = dropRepository.findById(dropId)
                .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));

        return DropResponse.from(drop);
    }

    @Transactional
    public DropResponse update(Long sellerId, Long dropId, DropUpdateRequest request) {
        Drop drop = dropRepository.findById(dropId)
                .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));

        if (!Objects.equals(drop.getProduct().getSeller().getId(), sellerId)) {
            throw new ServiceException(DropErrorCode.DROP_OWNER_REQUIRED);
        }

        drop.ensureEditable(LocalDateTime.now());

        drop.update(request.initialQuantity(), request.discountRate(), request.purchaseLimit(), request.openAt(), request.closeAt());

        return DropResponse.from(drop);
    }

    @Transactional
    public void delete(Long sellerId, Long dropId) {
        Drop drop = dropRepository.findById(dropId)
                .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));

        if (!Objects.equals(drop.getProduct().getSeller().getId(), sellerId)) {
            throw new ServiceException(DropErrorCode.DROP_OWNER_REQUIRED);
        }

        drop.ensureEditable(LocalDateTime.now());

        dropRepository.delete(drop);
    }
}
