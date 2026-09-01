package com.dropit.drop.controller;

import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.entity.DropStatus;
import com.dropit.drop.service.DropService;
import com.dropit.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DropControllerTest {

    @Mock
    private DropService dropService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new DropController(dropService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("원화 단위 가격으로 드랍 생성 요청 시 201 상태와 드랍 ID를 반환한다")
    void createDrop() throws Exception {
        when(dropService.save(eq(1L), any())).thenReturn(100L);

        mockMvc.perform(post("/drops")
                        .queryParam("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 10,
                                  "price": 59000,
                                  "initialQuantity": 100,
                                  "discountRate": 20,
                                  "purchaseLimit": 2,
                                  "openAt": "2026-09-01T12:00:00",
                                  "closeAt": "2026-09-02T12:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/drops/100"))
                .andExpect(jsonPath("$").value(100));
    }

    @Test
    @DisplayName("유효하지 않은 드랍 생성 요청은 400 오류를 반환한다")
    void rejectInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/drops")
                        .queryParam("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 10,
                                  "price": 0,
                                  "initialQuantity": 0,
                                  "discountRate": 101,
                                  "openAt": "2026-09-01T12:00:00",
                                  "closeAt": "2026-09-02T12:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors.initialQuantity").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.discountRate").exists())
                .andExpect(jsonPath("$.errors.purchaseLimit").exists());
    }

    @Test
    @DisplayName("드랍 목록과 상세 조회 요청 시 200 상태를 반환한다")
    void getAllAndOneDrop() throws Exception {
        DropResponse response = response();
        when(dropService.getAll()).thenReturn(List.of(response));
        when(dropService.getOne(100L)).thenReturn(response);

        mockMvc.perform(get("/drops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100));
        mockMvc.perform(get("/drops/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1));
    }

    @Test
    @DisplayName("드랍 수정 요청 시 200 상태를 반환한다")
    void updateDrop() throws Exception {
        when(dropService.update(eq(1L), eq(100L), any())).thenReturn(response());

        mockMvc.perform(patch("/drops/100")
                        .queryParam("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "price": 49000,
                                  "initialQuantity": 100,
                                  "discountRate": 30,
                                  "purchaseLimit": 2,
                                  "openAt": "2026-09-01T12:00:00",
                                  "closeAt": "2026-09-02T12:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    @DisplayName("드랍 공개 여부 변경 요청 시 200 상태를 반환한다")
    void changeVisibility() throws Exception {
        when(dropService.changeVisibility(eq(1L), eq(100L), any())).thenReturn(response());

        mockMvc.perform(patch("/drops/100/visibility")
                        .queryParam("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "visible": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(true));
    }

    @Test
    @DisplayName("드랍 삭제 요청 시 204 상태를 반환한다")
    void deleteDrop() throws Exception {
        mockMvc.perform(delete("/drops/100").queryParam("userId", "1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("공개 여부가 없는 변경 요청은 400 오류를 반환한다")
    void rejectVisibilityRequestWithoutVisible() throws Exception {
        mockMvc.perform(patch("/drops/100/visibility")
                        .queryParam("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors.visible").exists());
    }

    private DropResponse response() {
        LocalDateTime openAt = LocalDateTime.of(2026, 9, 1, 12, 0);
        return new DropResponse(
                100L,
                10L,
                "seller",
                1L,
                "Limited Hoodie",
                null,
                new BigDecimal("59000"),
                20,
                100,
                100,
                0,
                2,
                true,
                openAt,
                openAt.plusDays(1),
                DropStatus.READY
        );
    }
}
