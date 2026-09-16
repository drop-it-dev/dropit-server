package com.dropit.ranking.controller;

import com.dropit.global.exception.GlobalExceptionHandler;
import com.dropit.global.exception.ServiceException;
import com.dropit.ranking.dto.request.RankingRange;
import com.dropit.ranking.dto.response.SellerRankingEntryResponse;
import com.dropit.ranking.dto.response.SellerRankingResponse;
import com.dropit.ranking.exception.RankingErrorCode;
import com.dropit.ranking.service.SellerRankingQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SellerRankingControllerTest {

    private SellerRankingQueryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(SellerRankingQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SellerRankingController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsRankingMetadataTopListAndRequestedSeller() throws Exception {
        var requested = new SellerRankingEntryResponse(3, 10, 7);
        var response = new SellerRankingResponse(UUID.randomUUID(), RankingRange.TOTAL,
                YearMonth.parse("2026-09"), Instant.parse("2026-09-16T03:00:00Z"),
                Instant.parse("2026-09-16T03:00:02Z"),
                List.of(new SellerRankingEntryResponse(1, 20, 10)), requested);
        when(service.get(RankingRange.TOTAL, 5, 10L)).thenReturn(response);

        mockMvc.perform(get("/rankings/sellers")
                        .param("range", "TOTAL").param("limit", "5").param("sellerId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("TOTAL"))
                .andExpect(jsonPath("$.asOf").value("2026-09-16T03:00:00Z"))
                .andExpect(jsonPath("$.rankings[0].salesQuantity").value(10))
                .andExpect(jsonPath("$.requestedSeller.rank").value(3));
    }

    @Test
    void returnsServiceUnavailableBeforeFirstSuccessfulSnapshot() throws Exception {
        when(service.get(RankingRange.MONTHLY, 10, null))
                .thenThrow(new ServiceException(RankingErrorCode.RANKING_NOT_READY));

        mockMvc.perform(get("/rankings/sellers").param("range", "MONTHLY"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RANKING_NOT_READY"));
    }

}
