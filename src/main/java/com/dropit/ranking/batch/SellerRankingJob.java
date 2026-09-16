package com.dropit.ranking.batch;

import com.dropit.ranking.repository.SellerRankingExecutionStore;
import com.dropit.ranking.repository.SellerRankingSnapshotStore;
import com.dropit.ranking.repository.SellerSalesRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RequiredArgsConstructor
public class SellerRankingJob {

    private static final Logger log = LoggerFactory.getLogger(SellerRankingJob.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private final SellerSalesRepository repository;
    private final SellerRankingSnapshotStore snapshots;
    private final SellerRankingExecutionStore executions;
    private final Clock clock;
    private final ZoneId orderTimeZone;

    public void run(Instant requestedAsOf) {
        Instant startedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant asOf = requestedAsOf == null ? startedAt.truncatedTo(ChronoUnit.HOURS) : requestedAsOf;
        if (asOf.isAfter(startedAt) || !asOf.equals(asOf.truncatedTo(ChronoUnit.HOURS))) {
            throw new IllegalArgumentException("집계 기준은 미래가 아닌 정각의 ISO-8601 시각이어야 합니다.");
        }
        YearMonth month = YearMonth.from(asOf.atZone(BUSINESS_ZONE));
        var execution = new SellerRankingExecution(UUID.randomUUID(), month, asOf, startedAt);
        if (!executions.tryAcquire(execution)) {
            Instant completedAt = clock.instant();
            executions.recordSkipped(execution, completedAt, "다른 랭킹 집계가 실행 중입니다.");
            log.info("Seller ranking skipped: version={}, asOf={}, month={}, elapsedMs={}",
                    execution.version(), asOf, month, completedAt.toEpochMilli() - startedAt.toEpochMilli());
            return;
        }

        int sellerCount = 0;
        try {
            executions.recordStarted(execution);
            Instant monthStart = month.atDay(1).atStartOfDay(BUSINESS_ZONE).toInstant();
            log.info("Seller ranking started: version={}, asOf={}, month={}, orderTimeZone={}",
                    execution.version(), asOf, month, orderTimeZone);
            var sales = repository.aggregate(LocalDateTime.ofInstant(monthStart, orderTimeZone),
                    LocalDateTime.ofInstant(asOf, orderTimeZone));
            sellerCount = sales.size();
            boolean published = snapshots.publish(execution, clock.instant(), sales);
            log.info("Seller ranking completed: version={}, asOf={}, month={}, sellers={}, published={}, elapsedMs={}",
                    execution.version(), asOf, month, sellerCount, published,
                    clock.millis() - startedAt.toEpochMilli());
        } catch (RuntimeException failure) {
            Instant failedAt = clock.instant();
            try {
                executions.recordFailed(execution, failedAt, failure);
            } catch (RuntimeException historyFailure) {
                failure.addSuppressed(historyFailure);
            }
            log.error("Seller ranking failed: version={}, asOf={}, month={}, sellers={}, elapsedMs={}, reason={}",
                    execution.version(), asOf, month, sellerCount,
                    failedAt.toEpochMilli() - startedAt.toEpochMilli(), failure.toString(), failure);
            throw failure;
        } finally {
            try {
                executions.release(execution);
            } catch (RuntimeException releaseFailure) {
                log.error("Seller ranking lock release failed: version={}", execution.version(), releaseFailure);
            }
        }
    }
}
