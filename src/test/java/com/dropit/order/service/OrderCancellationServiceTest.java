package com.dropit.order.service;

import com.dropit.drop.entity.Drop;
import com.dropit.drop.repository.DropRepository;
import com.dropit.order.entity.DropUserPurchase;
import com.dropit.order.entity.Order;
import com.dropit.order.entity.OrderItem;
import com.dropit.order.repository.DropUserPurchaseRepository;
import com.dropit.order.repository.OrderItemRepository;
import com.dropit.order.repository.OrderRepository;
import com.dropit.order.repository.OrderRequestRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderCancellationServiceTest {

    @Test
    @DisplayName("취소 대상 항목은 dropId 순서로 조회해 구매량과 Drop 락 순서를 고정한다")
    @SuppressWarnings("unchecked")
    void acquireLocksInDropIdOrder() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderRequestRepository requestRepository = mock(OrderRequestRepository.class);
        OrderItemRepository itemRepository = mock(OrderItemRepository.class);
        DropUserPurchaseRepository purchaseRepository = mock(DropUserPurchaseRepository.class);
        DropRepository dropRepository = mock(DropRepository.class);
        OrderRequestRedisSyncService redisSyncService = mock(OrderRequestRedisSyncService.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(transactionStatus));

        Order order = order();
        OrderItem first = item(10L, order);
        OrderItem second = item(20L, order);
        Drop firstDrop = first.getDrop();
        Drop secondDrop = second.getDrop();
        when(requestRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.empty());
        when(orderRepository.findByIdAndUser_IdForUpdate(100L, 1L)).thenReturn(Optional.of(order));
        when(itemRepository.findAllForCancellationOrderByDropIdAsc(100L)).thenReturn(List.of(first, second));
        when(purchaseRepository.findByDropIdAndUserIdForUpdate(anyLong(), eq(1L)))
                .thenReturn(Optional.of(mock(DropUserPurchase.class)));
        when(dropRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(firstDrop));
        when(dropRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(secondDrop));
        when(purchaseRepository.decreaseConfirmedQuantity(anyLong(), eq(1L), eq(1))).thenReturn(1);

        new OrderCancellationService(
                orderRepository, requestRepository, itemRepository, purchaseRepository,
                dropRepository, redisSyncService, transactionTemplate
        ).cancel(1L, 100L);

        verify(itemRepository).findAllForCancellationOrderByDropIdAsc(100L);
        var locks = inOrder(purchaseRepository, dropRepository);
        locks.verify(purchaseRepository).findByDropIdAndUserIdForUpdate(10L, 1L);
        locks.verify(dropRepository).findByIdForUpdate(10L);
        locks.verify(purchaseRepository).findByDropIdAndUserIdForUpdate(20L, 1L);
        locks.verify(dropRepository).findByIdForUpdate(20L);
    }

    private Order order() {
        User buyer = new User("buyer@example.com", "password", "buyer", UserRole.USER);
        ReflectionTestUtils.setField(buyer, "id", 1L);
        Order order = new Order(buyer, new BigDecimal("2000"));
        ReflectionTestUtils.setField(order, "id", 100L);
        return order;
    }

    private OrderItem item(Long dropId, Order order) {
        Drop drop = mock(Drop.class);
        when(drop.getId()).thenReturn(dropId);
        OrderItem item = mock(OrderItem.class);
        when(item.getDrop()).thenReturn(drop);
        when(item.getQuantity()).thenReturn(1);
        return item;
    }
}
