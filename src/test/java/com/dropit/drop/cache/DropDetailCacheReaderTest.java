package com.dropit.drop.cache;

import com.dropit.drop.entity.Drop;
import com.dropit.drop.exception.DropErrorCode;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.exception.ServiceException;
import com.dropit.product.entity.Product;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropDetailCacheReaderTest {

    @Mock
    private DropRepository dropRepository;

    @InjectMocks
    private DropDetailCacheReader cacheReader;

    @Test
    void hiddenDropIsNotCachedAsPublicDetail() {
        User seller = new User("seller@example.com", "password", "seller", UserRole.SELLER);
        Product product = new Product(seller, "Limited Hoodie", "description", null);
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        Drop hiddenDrop = new Drop(
                product,
                new BigDecimal("59000"),
                10,
                20,
                2,
                openAt,
                openAt.plusDays(1)
        );
        when(dropRepository.findDetailById(100L)).thenReturn(Optional.of(hiddenDrop));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> cacheReader.get(100L)
        );

        assertEquals(DropErrorCode.DROP_NOT_FOUND, exception.getErrorCode());
    }
}
