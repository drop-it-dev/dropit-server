package com.dropit.drop.cache;

import com.dropit.drop.exception.DropErrorCode;
import com.dropit.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropStockReaderTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private DropStockReader stockReader;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        stockReader = new DropStockReader(redisTemplate);
    }

    @Test
    void readsCurrentStockFromOrderRedisKey() {
        when(valueOperations.get(anyString())).thenReturn("97");

        int remainingQuantity = stockReader.getRemainingQuantity(100L);

        assertEquals(97, remainingQuantity);
    }

    @Test
    void rejectsMissingStockInsteadOfReturningStaleDatabaseValue() {
        when(valueOperations.get(anyString())).thenReturn(null);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> stockReader.getRemainingQuantity(100L)
        );

        assertEquals(DropErrorCode.DROP_STOCK_NOT_READY, exception.getErrorCode());
    }

    @Test
    void rejectsInvalidStockValue() {
        when(valueOperations.get(anyString())).thenReturn("invalid");

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> stockReader.getRemainingQuantity(100L)
        );

        assertEquals(DropErrorCode.DROP_STOCK_NOT_READY, exception.getErrorCode());
    }
}
