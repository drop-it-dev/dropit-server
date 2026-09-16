package com.dropit.ranking.repository;

import com.dropit.global.exception.ServiceException;
import com.dropit.ranking.dto.request.RankingRange;
import com.dropit.ranking.exception.RankingErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class SellerRankingQueryStoreTest {

    @Test
    void wrapsMalformedOkResponsesAsRankingUnavailable() {
        assertMalformed(List.of("OK"));

        var invalidVersion = okResponse();
        invalidVersion.set(1, "version");
        assertMalformed(invalidVersion);

        var invalidTimestamp = okResponse();
        invalidTimestamp.set(2, "not-a-timestamp");
        assertMalformed(invalidTimestamp);

        var invalidMonth = okResponse();
        invalidMonth.set(4, "not-a-month");
        assertMalformed(invalidMonth);

        var invalidCount = okResponse();
        invalidCount.set(5, "not-a-number");
        assertMalformed(invalidCount);

        var missingRows = okResponse();
        missingRows.set(5, "1");
        assertMalformed(missingRows);

        var undecodableVersion = okResponse();
        undecodableVersion.set(1, new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("Cannot decode value");
            }
        });
        assertMalformed(undecodableVersion);
    }

    private List<Object> okResponse() {
        return new ArrayList<>(List.of("OK", "6c5e53ba-78be-4cd6-b237-983d72c5d6c3",
                "2026-09-16T03:00:00Z", "2026-09-16T03:00:00Z", "2026-09", "0", "", ""));
    }

    private void assertMalformed(List<?> response) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(response).when(redis).execute(any(), anyList(), any(Object[].class));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> new SellerRankingQueryStore(redis).read(
                        RankingRange.TOTAL, YearMonth.parse("2026-09"), 10, null));

        assertEquals(RankingErrorCode.RANKING_UNAVAILABLE, exception.getErrorCode());
        assertNotNull(exception.getCause());
    }
}
