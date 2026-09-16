package com.dropit.ranking.controller;

import com.dropit.documentation.DocumentationTestSupport;
import com.dropit.ranking.dto.request.RankingRange;
import com.dropit.ranking.dto.response.SellerRankingEntryResponse;
import com.dropit.ranking.dto.response.SellerRankingResponse;
import com.dropit.ranking.service.SellerRankingQueryService;
import com.epages.restdocs.apispec.Schema;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.FieldDescriptor;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.ResourceSnippetParameters.builder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.JsonFieldType.ARRAY;
import static org.springframework.restdocs.payload.JsonFieldType.NUMBER;
import static org.springframework.restdocs.payload.JsonFieldType.STRING;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(RestDocumentationExtension.class)
class SellerRankingDocumentationTest extends DocumentationTestSupport {

    private SellerRankingQueryService service;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        service = mock(SellerRankingQueryService.class);
        configure(restDocumentation, new SellerRankingController(service));
    }

    @Test
    void getRanking() throws Exception {
        when(service.get(RankingRange.MONTHLY, 5, 10L)).thenReturn(new SellerRankingResponse(
                UUID.randomUUID(), RankingRange.MONTHLY, YearMonth.parse("2026-09"),
                Instant.parse("2026-09-16T03:00:00Z"), Instant.parse("2026-09-16T03:00:02Z"),
                List.of(new SellerRankingEntryResponse(1, 10, 20)),
                new SellerRankingEntryResponse(1, 10, 20)));

        mockMvc.perform(get("/rankings/sellers")
                        .param("range", "MONTHLY").param("limit", "5").param("sellerId", "10"))
                .andExpect(status().isOk())
                .andDo(document("seller-rankings-get", resource(builder()
                        .tag("Rankings").summary("판매자 랭킹 조회")
                        .description("집계된 판매량 기준으로 판매자 랭킹을 조회합니다.")
                        .queryParameters(
                                parameterWithName("range").optional().description("집계 범위 (TOTAL 또는 MONTHLY)"),
                                parameterWithName("limit").optional().description("조회할 랭킹 수 (1~100)"),
                                parameterWithName("sellerId").optional().description("함께 조회할 판매자 ID")
                        ).responseSchema(new Schema("SellerRankingResponse")).responseFields(rankingResponseFields()).build())));
    }

    @Test
    void getRankingWithoutSellerId() throws Exception {
        when(service.get(RankingRange.MONTHLY, 5, null)).thenReturn(new SellerRankingResponse(
                UUID.randomUUID(), RankingRange.MONTHLY, YearMonth.parse("2026-09"),
                Instant.parse("2026-09-16T03:00:00Z"), Instant.parse("2026-09-16T03:00:02Z"),
                List.of(new SellerRankingEntryResponse(1, 10, 20)), null));

        mockMvc.perform(get("/rankings/sellers").param("range", "MONTHLY").param("limit", "5"))
                .andExpect(status().isOk())
                .andDo(document("seller-rankings-get-without-seller-id", resource(builder()
                        .tag("Rankings").summary("판매자 랭킹 조회")
                        .description("집계된 판매량 기준으로 판매자 랭킹을 조회합니다.")
                        .queryParameters(
                                parameterWithName("range").optional().description("집계 범위 (TOTAL 또는 MONTHLY)"),
                                parameterWithName("limit").optional().description("조회할 랭킹 수 (1~100)"),
                                parameterWithName("sellerId").optional().description("함께 조회할 판매자 ID")
                        ).responseSchema(new Schema("SellerRankingResponse")).responseFields(rankingResponseFields()).build())));
    }

    private static FieldDescriptor[] rankingResponseFields() {
        return new FieldDescriptor[]{
                fieldWithPath("version").type(STRING).description("랭킹 스냅샷 버전"),
                fieldWithPath("range").type(STRING).description("집계 범위"),
                fieldWithPath("month").type(STRING).description("월간 집계 대상 월"),
                fieldWithPath("asOf").type(STRING).description("집계 기준 시각"),
                fieldWithPath("publishedAt").type(STRING).description("스냅샷 발행 시각"),
                fieldWithPath("rankings").type(ARRAY).description("상위 판매자 랭킹 목록"),
                fieldWithPath("rankings[].rank").type(NUMBER).description("랭킹 순위"),
                fieldWithPath("rankings[].sellerId").type(NUMBER).description("판매자 ID"),
                fieldWithPath("rankings[].salesQuantity").type(NUMBER).description("판매 수량"),
                fieldWithPath("requestedSeller").optional().description("요청 판매자 랭킹"),
                fieldWithPath("requestedSeller.rank").type(NUMBER).optional().description("요청 판매자의 순위"),
                fieldWithPath("requestedSeller.sellerId").type(NUMBER).optional().description("요청 판매자 ID"),
                fieldWithPath("requestedSeller.salesQuantity").type(NUMBER).optional().description("요청 판매자의 판매 수량")
        };
    }
}
