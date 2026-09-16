package com.dropit.ranking.batch;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

public record SellerRankingExecution(
        UUID version,
        YearMonth month,
        Instant asOf,
        Instant startedAt
) {
}
