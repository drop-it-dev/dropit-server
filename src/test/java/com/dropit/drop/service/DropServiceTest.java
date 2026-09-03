package com.dropit.drop.service;

import com.dropit.drop.dto.request.DropCreateRequest;
import com.dropit.drop.dto.request.DropUpdateRequest;
import com.dropit.drop.dto.request.DropVisibilityUpdateRequest;
import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.exception.DropErrorCode;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.exception.ServiceException;
import com.dropit.product.entity.Product;
import com.dropit.product.exception.ProductErrorCode;
import com.dropit.product.repository.ProductRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DropServiceTest {

    @Mock
    private DropRepository dropRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private DropService dropService;

    @Test
    @DisplayName("상품 소유자는 드랍을 생성할 수 있다")
    void saveDropOwnedBySeller() {
        Product product = saveProduct(1L, 10L);
        DropCreateRequest request = saveRequest();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(dropRepository.save(any(Drop.class))).thenAnswer(invocation -> {
            Drop drop = invocation.getArgument(0);
            ReflectionTestUtils.setField(drop, "id", 100L);
            return drop;
        });

        Long dropId = dropService.save(1L, request);

        assertEquals(100L, dropId);
        ArgumentCaptor<Drop> captor = ArgumentCaptor.forClass(Drop.class);
        verify(dropRepository).save(captor.capture());
        Drop savedDrop = captor.getValue();
        assertEquals(product, savedDrop.getProduct());
        assertEquals(new BigDecimal("59000"), savedDrop.getPrice());
        assertEquals(10, savedDrop.getInitialQuantity());
        assertEquals(20, savedDrop.getDiscountRate());
        assertEquals(2, savedDrop.getPurchaseLimit());
    }

    @Test
    @DisplayName("존재하지 않는 상품으로 드랍을 생성할 수 없다")
    void rejectMissingProduct() {
        when(productRepository.findById(10L)).thenReturn(Optional.empty());

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> dropService.save(1L, saveRequest())
        );

        assertEquals(ProductErrorCode.PRODUCT_NOT_FOUND, exception.getErrorCode());
        verify(dropRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 판매자의 상품으로 드랍을 생성할 수 없다")
    void rejectCreatingDropForAnotherSellersProduct() {
        Product product = saveProduct(1L, 10L);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> dropService.save(2L, saveRequest())
        );

        assertEquals(ProductErrorCode.PRODUCT_OWNER_REQUIRED, exception.getErrorCode());
        verify(dropRepository, never()).save(any());
    }

    @Test
    @DisplayName("공개된 드랍 상세 정보를 조회할 수 있다")
    void getOneDrop() {
        Drop drop = saveDrop(1L, 10L, 100L, LocalDateTime.now().plusDays(1));
        when(dropRepository.findById(100L)).thenReturn(Optional.of(drop));

        DropResponse response = dropService.getOne(100L);

        assertEquals(100L, response.id());
        assertEquals(10L, response.productId());
    }

    @Test
    @DisplayName("공개된 드랍 목록을 조회할 수 있다")
    void getAllDrops() {
        Drop first = saveDrop(1L, 10L, 100L, LocalDateTime.now().plusDays(1));
        Drop second = saveDrop(1L, 11L, 101L, LocalDateTime.now().plusDays(2));
        when(dropRepository.findAll()).thenReturn(List.of(first, second));

        List<DropResponse> responses = dropService.getAll();

        assertEquals(2, responses.size());
        assertEquals(100L, responses.get(0).id());
        assertEquals(101L, responses.get(1).id());
    }

    @Test
    @DisplayName("비공개 드랍은 공개 목록에서 제외된다")
    void excludeHiddenDropsFromPublicList() {
        Drop visibleDrop = saveDrop(1L, 10L, 100L, LocalDateTime.now().plusDays(1));
        Product product = saveProduct(1L, 11L);
        Drop hiddenDrop = new Drop(
                product,
                new BigDecimal("59000"),
                10,
                20,
                2,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(2)
        );
        ReflectionTestUtils.setField(hiddenDrop, "id", 101L);
        when(dropRepository.findAll()).thenReturn(List.of(visibleDrop, hiddenDrop));

        List<DropResponse> responses = dropService.getAll();

        assertEquals(1, responses.size());
        assertEquals(100L, responses.get(0).id());
    }

    @Test
    @DisplayName("비공개 드랍 상세 조회는 존재하지 않는 드랍으로 처리한다")
    void rejectHiddenDropFromPublicDetail() {
        Product product = saveProduct(1L, 10L);
        Drop hiddenDrop = new Drop(
                product,
                new BigDecimal("59000"),
                10,
                20,
                2,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(2)
        );
        when(dropRepository.findById(100L)).thenReturn(Optional.of(hiddenDrop));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> dropService.getOne(100L)
        );

        assertEquals(DropErrorCode.DROP_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("판매 시작 전 소유자는 드랍 가격과 판매 조건을 수정할 수 있다")
    void updateOwnedDropBeforeOpen() {
        Drop drop = saveDrop(1L, 10L, 100L, LocalDateTime.now().plusDays(2));
        when(dropRepository.findById(100L)).thenReturn(Optional.of(drop));
        DropUpdateRequest request = new DropUpdateRequest(
                new BigDecimal("49000"),
                20,
                30,
                3,
                LocalDateTime.now().plusDays(3),
                LocalDateTime.now().plusDays(4)
        );

        DropResponse response = dropService.update(1L, 100L, request);

        assertEquals(20, response.initialQuantity());
        assertEquals(new BigDecimal("49000"), response.price());
        assertEquals(20, response.remainingQuantity());
        assertEquals(30, response.discountRate());
        assertEquals(3, response.purchaseLimit());
        verify(dropRepository, never()).save(any());
    }

    @Test
    @DisplayName("구매 제한 수량은 0으로 변경하여 무제한으로 설정할 수 있다")
    void updatePurchaseLimitToUnlimited() {
        Drop drop = saveDrop(1L, 10L, 100L, LocalDateTime.now().plusDays(2));
        when(dropRepository.findById(100L)).thenReturn(Optional.of(drop));
        DropUpdateRequest request = new DropUpdateRequest(
                null,
                10,
                20,
                0,
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(3)
        );

        DropResponse response = dropService.update(1L, 100L, request);

        assertEquals(0, response.purchaseLimit());
    }

    @Test
    @DisplayName("다른 판매자는 드랍을 수정할 수 없다")
    void rejectUpdatingAnotherSellersDrop() {
        Drop drop = saveDrop(1L, 10L, 100L, LocalDateTime.now().plusDays(1));
        when(dropRepository.findById(100L)).thenReturn(Optional.of(drop));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> dropService.update(2L, 100L, new DropUpdateRequest(
                        null,
                        10,
                        30,
                        null,
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(2)
                ))
        );

        assertEquals(DropErrorCode.DROP_OWNER_REQUIRED, exception.getErrorCode());
    }

    @Test
    @DisplayName("드랍 소유자는 공개 여부를 변경할 수 있다")
    void changeVisibilityOwnedBySeller() {
        Drop drop = saveDrop(1L, 10L, 100L, LocalDateTime.now().plusDays(1));
        when(dropRepository.findById(100L)).thenReturn(Optional.of(drop));

        DropResponse response = dropService.changeVisibility(
                1L,
                100L,
                new DropVisibilityUpdateRequest(false)
        );

        assertEquals(false, response.visible());
    }

    @Test
    @DisplayName("다른 판매자는 드랍 공개 여부를 변경할 수 없다")
    void rejectChangingVisibilityOfAnotherSellersDrop() {
        Drop drop = saveDrop(1L, 10L, 100L, LocalDateTime.now().plusDays(1));
        when(dropRepository.findById(100L)).thenReturn(Optional.of(drop));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> dropService.changeVisibility(2L, 100L, new DropVisibilityUpdateRequest(false))
        );

        assertEquals(DropErrorCode.DROP_OWNER_REQUIRED, exception.getErrorCode());
        assertEquals(true, drop.isVisible());
    }

    @Test
    @DisplayName("판매가 시작된 드랍은 삭제할 수 없다")
    void rejectDeletingStartedDrop() {
        Drop drop = saveDrop(1L, 10L, 100L, LocalDateTime.now().minusMinutes(1));
        when(dropRepository.findById(100L)).thenReturn(Optional.of(drop));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> dropService.delete(1L, 100L)
        );

        assertEquals(DropErrorCode.DROP_ALREADY_STARTED, exception.getErrorCode());
        verify(dropRepository, never()).delete(any());
    }

    private DropCreateRequest saveRequest() {
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        return new DropCreateRequest(10L, new BigDecimal("59000"), 10, 20, 2, openAt, openAt.plusDays(1));
    }

    private Drop saveDrop(Long sellerId, Long productId, Long dropId, LocalDateTime openAt) {
        Product product = saveProduct(sellerId, productId);
        Drop drop = new Drop(product, new BigDecimal("59000"), 10, 20, 2, openAt, openAt.plusDays(1));
        drop.changeVisibility(true);
        ReflectionTestUtils.setField(drop, "id", dropId);
        return drop;
    }

    private Product saveProduct(Long sellerId, Long productId) {
        User seller = new User("seller@example.com", "password", "seller", UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", sellerId);
        Product product = new Product(
                seller,
                "Limited Hoodie",
                "Limited edition hoodie",
                null
        );
        ReflectionTestUtils.setField(product, "id", productId);
        return product;
    }
}
