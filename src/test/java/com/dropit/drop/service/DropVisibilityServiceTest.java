package com.dropit.drop.service;

import com.dropit.drop.cache.DropSaleCacheWriter;
import com.dropit.drop.cache.DropSaleSnapshot;
import com.dropit.drop.dto.request.DropVisibilityUpdateRequest;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.repository.DropRepository;
import com.dropit.order.repository.DropUserPurchaseRepository;
import com.dropit.order.repository.OrderItemRepository;
import com.dropit.product.entity.Product;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DropVisibilityServiceTest {

    @Test
    @DisplayName("최초 공개는 판매 조건을 동결하고 DB 트랜잭션 뒤 Redis에 준비한다")
    @SuppressWarnings("unchecked")
    void prepareSaleOnFirstPublication() {
        DropRepository dropRepository = mock(DropRepository.class);
        OrderItemRepository itemRepository = mock(OrderItemRepository.class);
        DropUserPurchaseRepository purchaseRepository = mock(DropUserPurchaseRepository.class);
        DropSaleCacheWriter cacheWriter = mock(DropSaleCacheWriter.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
        Drop drop = drop();
        when(dropRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(drop));

        var response = new DropVisibilityService(
                dropRepository, itemRepository, purchaseRepository, cacheWriter, transactionTemplate)
                .changeVisibility(1L, 100L, new DropVisibilityUpdateRequest(true));

        assertAll(
                () -> assertTrue(response.visible()),
                () -> assertTrue(drop.isSalePrepared()),
                () -> assertEquals(1L, drop.getSaleVersion())
        );
        verify(cacheWriter).apply(any(DropSaleSnapshot.class));
    }

    @Test
    @DisplayName("재공개는 준비된 판매 재고를 초기화하는 새 saleVersion을 만들지 않는다")
    @SuppressWarnings("unchecked")
    void doNotCreateNewSaleVersionOnReplay() {
        DropRepository dropRepository = mock(DropRepository.class);
        OrderItemRepository itemRepository = mock(OrderItemRepository.class);
        DropUserPurchaseRepository purchaseRepository = mock(DropUserPurchaseRepository.class);
        DropSaleCacheWriter cacheWriter = mock(DropSaleCacheWriter.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
        Drop drop = drop();
        drop.prepareSale();
        drop.changeVisibility(false);
        when(dropRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(drop));

        new DropVisibilityService(dropRepository, itemRepository, purchaseRepository, cacheWriter, transactionTemplate)
                .changeVisibility(1L, 100L, new DropVisibilityUpdateRequest(true));

        assertEquals(1L, drop.getSaleVersion());
        verifyNoInteractions(itemRepository, purchaseRepository);
    }

    private Drop drop() {
        User seller = new User("seller@example.com", "password", "seller", UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", 1L);
        Product product = new Product(seller, "product", "description", null);
        Drop drop = new Drop(product, new BigDecimal("1000"), 10, 0, 3,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));
        ReflectionTestUtils.setField(drop, "id", 100L);
        return drop;
    }
}
