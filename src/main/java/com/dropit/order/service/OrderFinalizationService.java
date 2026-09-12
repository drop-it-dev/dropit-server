package com.dropit.order.service;

import com.dropit.drop.entity.Drop;
import com.dropit.drop.exception.DropErrorCode;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.exception.ServiceException;
import com.dropit.order.dto.request.OrderFinalizationRequest;
import com.dropit.order.entity.Order;
import com.dropit.order.entity.OrderItem;
import com.dropit.order.entity.OrderRequest;
import com.dropit.order.entity.OrderRequestStatus;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.order.repository.DropUserPurchaseRepository;
import com.dropit.order.repository.OrderItemRepository;
import com.dropit.order.repository.OrderRepository;
import com.dropit.order.repository.OrderRequestRepository;
import com.dropit.order.validation.OrderFinalizationValidator;
import com.dropit.user.entity.User;
import com.dropit.user.exception.UserErrorCode;
import com.dropit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Consumer가 전달한 주문 요청을 MySQL 트랜잭션으로 중복 없이 최종 주문 처리
 */
@Service
@RequiredArgsConstructor
public class OrderFinalizationService {

    private final OrderRequestRepository orderRequestRepository;
    private final DropUserPurchaseRepository dropUserPurchaseRepository;
    private final DropRepository dropRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public OrderRequestStatus finalizeOrder(OrderFinalizationRequest input) {
        // 1. 입력 검증 및 처리 대상 Drop 확인
        OrderFinalizationValidator.validate(input);

        Optional<OrderRequest> knownRequest = orderRequestRepository.findById(input.requestId());
        Long dropIdToLock = knownRequest.map(OrderRequest::getDropId).orElse(input.dropId());
        // 모든 주문 확정 경로는 Drop을 먼저 잠근 뒤 OrderRequest를 잠근다.
        Drop drop = dropRepository.findByIdForUpdate(dropIdToLock)
                .orElseThrow(() -> new ServiceException(DropErrorCode.DROP_NOT_FOUND));

        // 2. 접수 기록 확인 및 중복 처리 방지
        OrderRequest request = orderRequestRepository.findByRequestIdForUpdate(input.requestId())
                .orElseGet(() -> createRequest(input));

        OrderFinalizationValidator.validateStoredRequest(request, input);
        if (request.isTerminal()) {
            return request.getStatus();
        }

        // 3. 유저, 재고, 구매한도 검증
        User user;
        try {
            user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ServiceException(UserErrorCode.USER_NOT_FOUND));
            drop.validateStockForFinalization(request.getQuantity());
            reservePurchaseLimit(user.getId(), drop, request.getQuantity());
        } catch (ServiceException exception) {
            request.fail(exception.getErrorCode().toString());
            return request.getStatus();
        }

        // 4. 재고 차감 및 주문 생성
        drop.decreaseStockForFinalization(request.getQuantity());

        BigDecimal totalPrice = OrderItem.calculateItemTotalPrice(
                request.getUnitPrice(),
                request.getDiscountRate(),
                request.getQuantity()
        );
        Order order = orderRepository.save(new Order(user, totalPrice));
        orderItemRepository.save(new OrderItem(
                order,
                drop,
                request.getProductName(),
                request.getUnitPrice(),
                request.getDiscountRate(),
                request.getQuantity()
        ));
        request.succeed(order);
        return request.getStatus();
    }

    private OrderRequest createRequest(OrderFinalizationRequest input) {
        OrderRequest existingRequest = orderRequestRepository
                .findByUserIdAndDropIdAndIdempotencyKeyHashForUpdate(
                        input.userId(),
                        input.dropId(),
                        input.idempotencyKeyHash()
                )
                .orElse(null);
        if (existingRequest != null) {
            throw new ServiceException(OrderErrorCode.IDEMPOTENCY_KEY_REUSED);
        }

        return orderRequestRepository.saveAndFlush(new OrderRequest(
                input.requestId(),
                input.userId(),
                input.dropId(),
                input.idempotencyKeyHash(),
                input.payloadHash(),
                input.productName(),
                input.unitPrice(),
                input.quantity(),
                input.discountRate()
        ));
    }

    private void reservePurchaseLimit(Long userId, Drop drop, int quantity) {
        dropUserPurchaseRepository.createCounterIfAbsent(drop.getId(), userId);
        int increasedCount = dropUserPurchaseRepository.increaseWithinLimit(
                drop.getId(),
                userId,
                quantity,
                drop.getPurchaseLimit()
        );
        if (increasedCount != 1) {
            throw new ServiceException(OrderErrorCode.PURCHASE_LIMIT_EXCEEDED);
        }
    }
}
