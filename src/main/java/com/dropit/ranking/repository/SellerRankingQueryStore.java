package com.dropit.ranking.repository;

import com.dropit.global.exception.ServiceException;
import com.dropit.ranking.dto.request.RankingRange;
import com.dropit.ranking.dto.response.SellerRankingEntryResponse;
import com.dropit.ranking.dto.response.SellerRankingResponse;
import com.dropit.ranking.exception.RankingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SellerRankingQueryStore {

    private static final DefaultRedisScript<List> READ = new DefaultRedisScript<>();

    static {
        READ.setLocation(new ClassPathResource("redis/ranking/read-seller-ranking.lua"));
        READ.setResultType(List.class);
    }

    private final StringRedisTemplate redis;

    public SellerRankingResponse read(RankingRange range, YearMonth currentMonth,
                                      int limit, Long sellerId) {
        String rankingKey = range == RankingRange.TOTAL
                ? SellerRankingSnapshotStore.TOTAL_KEY
                : SellerRankingSnapshotStore.monthlyKey(currentMonth);
        List<?> result = redis.execute(READ,
                List.of(SellerRankingSnapshotStore.METADATA_KEY, rankingKey),
                range.name(), currentMonth.toString(), Integer.toString(limit),
                sellerId == null ? "" : sellerId.toString());
        if (result == null || result.isEmpty()) {
            throw new ServiceException(RankingErrorCode.RANKING_UNAVAILABLE);
        }
        String status = text(result.getFirst());
        if ("NOT_READY".equals(status)) {
            throw new ServiceException(RankingErrorCode.RANKING_NOT_READY);
        }
        if (!"OK".equals(status)) {
            throw new ServiceException(RankingErrorCode.RANKING_UNAVAILABLE);
        }

        UUID version = UUID.fromString(text(result.get(1)));
        Instant asOf = Instant.parse(text(result.get(2)));
        Instant publishedAt = Instant.parse(text(result.get(3)));
        YearMonth month = YearMonth.parse(text(result.get(4)));
        int itemCount = Integer.parseInt(text(result.get(5)));
        var rankings = new ArrayList<SellerRankingEntryResponse>(itemCount);
        int index = 6;
        for (int rank = 1; rank <= itemCount; rank++) {
            rankings.add(new SellerRankingEntryResponse(rank,
                    Long.parseLong(text(result.get(index++))), quantity(result.get(index++))));
        }
        SellerRankingEntryResponse requestedSeller = null;
        if (sellerId != null && !"".equals(text(result.get(index)))) {
            requestedSeller = new SellerRankingEntryResponse(
                    Long.parseLong(text(result.get(index))) + 1,
                    sellerId,
                    quantity(result.get(index + 1)));
        }
        return new SellerRankingResponse(version, range, month, asOf, publishedAt,
                List.copyOf(rankings), requestedSeller);
    }

    private static long quantity(Object value) {
        return new BigDecimal(text(value)).longValueExact();
    }

    private static String text(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value == null ? "" : value.toString();
    }
}
