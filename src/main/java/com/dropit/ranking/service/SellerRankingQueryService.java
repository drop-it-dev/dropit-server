package com.dropit.ranking.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.ranking.dto.request.RankingRange;
import com.dropit.ranking.dto.response.SellerRankingResponse;
import com.dropit.ranking.exception.RankingErrorCode;
import com.dropit.ranking.repository.SellerRankingQueryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class SellerRankingQueryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private final SellerRankingQueryStore store;
    private final Clock clock = Clock.systemUTC();

    public SellerRankingResponse get(RankingRange range, int limit, Long sellerId) {
        try {
            return store.read(range, YearMonth.now(clock.withZone(BUSINESS_ZONE)), limit, sellerId);
        } catch (ServiceException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new ServiceException(RankingErrorCode.RANKING_UNAVAILABLE, exception);
        }
    }
}
