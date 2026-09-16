package com.dropit.ranking.batch;

import com.dropit.ranking.repository.SellerRankingExecutionStore;
import com.dropit.ranking.repository.SellerRankingSnapshotStore;
import com.dropit.ranking.repository.SellerSales;
import com.dropit.ranking.repository.SellerSalesRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SellerRankingJobTest {

    private final SellerSalesRepository repository = mock(SellerSalesRepository.class);
    private final SellerRankingSnapshotStore snapshots = mock(SellerRankingSnapshotStore.class);
    private final SellerRankingExecutionStore executions = mock(SellerRankingExecutionStore.class);
    private final Instant now = Instant.parse("2026-09-30T15:02:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    void koreanMonthBoundaryIsConvertedToUtcStoredOrderTimes() {
        var job = job(ZoneOffset.UTC);
        var sales = List.of(new SellerSales(1L, 10L, 0L));
        when(executions.tryAcquire(any())).thenReturn(true);
        when(repository.aggregate(any(), any())).thenReturn(sales);

        job.run(null);

        verify(repository).aggregate(LocalDateTime.parse("2026-09-30T15:00:00"),
                LocalDateTime.parse("2026-09-30T15:00:00"));
        verify(snapshots).publish(argThat(execution ->
                execution.asOf().equals(Instant.parse("2026-09-30T15:00:00Z"))
                        && execution.month().equals(YearMonth.parse("2026-10"))
                        && execution.startedAt().equals(now)), eq(now), eq(sales));
        verify(executions).release(any());
    }

    @Test
    void delayedScheduleKeepsItsOriginalCutoffAndMonthWithSeoulStoredTimes() {
        var job = job(ZoneId.of("Asia/Seoul"));
        when(executions.tryAcquire(any())).thenReturn(true);
        when(repository.aggregate(any(), any())).thenReturn(List.of());

        job.run(Instant.parse("2026-09-30T14:00:00Z"));

        verify(repository).aggregate(LocalDateTime.parse("2026-09-01T00:00:00"),
                LocalDateTime.parse("2026-09-30T23:00:00"));
    }

    @Test
    void failedAggregationDoesNotPublish() {
        var job = job(ZoneOffset.UTC);
        when(executions.tryAcquire(any())).thenReturn(true);
        when(repository.aggregate(any(), any())).thenThrow(new IllegalStateException("DB unavailable"));

        assertThrows(IllegalStateException.class, () -> job.run(null));
        verifyNoInteractions(snapshots);
        verify(executions).recordFailed(any(), eq(now), any());
        verify(executions).release(any());
    }

    @Test
    void futureOrNonHourlyCutoffFailsBeforeReadingDatabase() {
        var job = job(ZoneOffset.UTC);
        assertThrows(IllegalArgumentException.class, () -> job.run(now.plusSeconds(3600)));
        assertThrows(IllegalArgumentException.class, () -> job.run(now.minusSeconds(3600)));
        verifyNoInteractions(repository, snapshots, executions);
    }

    @Test
    void skipsWithoutReadingDatabaseWhenAnotherExecutionHoldsTheLock() {
        var job = job(ZoneOffset.UTC);
        when(executions.tryAcquire(any())).thenReturn(false);

        job.run(null);

        verify(executions).recordSkipped(any(), eq(now), anyString());
        verifyNoInteractions(repository, snapshots);
        verify(executions, never()).release(any());
    }

    @Test
    void invalidOrInexactRedisScoresAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SellerSales(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SellerSales(1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SellerSales(1, 10, -1));
        assertThrows(IllegalArgumentException.class, () -> new SellerSales(1, 10, 11));
        assertThrows(IllegalArgumentException.class, () -> new SellerSales(1, 9_007_199_254_740_992L, 0));
    }

    private SellerRankingJob job(ZoneId orderTimeZone) {
        return new SellerRankingJob(repository, snapshots, executions, clock, orderTimeZone);
    }
}
