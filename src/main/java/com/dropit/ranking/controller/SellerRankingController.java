package com.dropit.ranking.controller;

import com.dropit.ranking.dto.request.RankingRange;
import com.dropit.ranking.dto.response.SellerRankingResponse;
import com.dropit.ranking.service.SellerRankingQueryService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/rankings/sellers")
@RequiredArgsConstructor
public class SellerRankingController {

    private final SellerRankingQueryService service;

    @PermitAll
    @GetMapping
    public ResponseEntity<SellerRankingResponse> get(
            @RequestParam(defaultValue = "TOTAL") RankingRange range,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Positive Long sellerId) {
        return ResponseEntity.ok(service.get(range, limit, sellerId));
    }
}
