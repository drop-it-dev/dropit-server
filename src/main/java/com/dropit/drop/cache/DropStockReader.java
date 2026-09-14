package com.dropit.drop.cache;

import com.dropit.drop.exception.DropErrorCode;
import com.dropit.global.exception.ServiceException;
import com.dropit.order.redis.RedisOrderKeyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DropStockReader {

    private final StringRedisTemplate redisTemplate;

    public int getRemainingQuantity(Long dropId) {
        try {
            String value = redisTemplate.opsForValue().get(
                    RedisOrderKeyFactory.stockKey(dropId)
            );

            if (value == null) {
                throw new ServiceException(DropErrorCode.DROP_STOCK_NOT_READY);
            }

            int remainingQuantity = Integer.parseInt(value);
            if (remainingQuantity < 0) {
                throw new ServiceException(DropErrorCode.DROP_STOCK_NOT_READY);
            }
            return remainingQuantity;
        } catch (NumberFormatException | DataAccessException exception) {
            throw new ServiceException(DropErrorCode.DROP_STOCK_NOT_READY, exception);
        }
    }
}
