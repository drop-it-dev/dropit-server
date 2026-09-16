package com.dropit.ranking.dto.response;

import com.dropit.ranking.dto.request.RankingRange;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record SellerRankingResponse(
        UUID version,
        RankingRange range,
        YearMonth month,
        Instant asOf,
        Instant publishedAt,
        List<SellerRankingEntryResponse> rankings,
        SellerRankingEntryResponse requestedSeller
) {
}
