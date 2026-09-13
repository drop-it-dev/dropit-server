package com.dropit.order.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.order.entity.OrderRequest;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.order.repository.OrderRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 주문요청 확정 실패 시 별도의 트랜잭션으로 failureCode 기록
 */
@Service
@RequiredArgsConstructor
public class OrderRequestFailureService {

    private final OrderRequestRepository orderRequestRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID requestId, String failureCode) {
        OrderRequest request = orderRequestRepository.findByRequestIdForUpdate(requestId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_REQUEST_NOT_FOUND));
        request.fail(failureCode);
    }
}
