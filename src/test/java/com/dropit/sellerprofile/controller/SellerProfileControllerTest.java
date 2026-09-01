package com.dropit.sellerprofile.controller;

import com.dropit.global.exception.GlobalExceptionHandler;
import com.dropit.global.exception.ServiceException;
import com.dropit.sellerprofile.dto.request.SellerProfileCreateRequest;
import com.dropit.sellerprofile.dto.request.SellerProfileUpdateRequest;
import com.dropit.sellerprofile.dto.response.SellerProfileResponse;
import com.dropit.sellerprofile.exception.SellerProfileErrorCode;
import com.dropit.sellerprofile.service.SellerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SellerProfileControllerTest {

    private MockMvc mockMvc;
    private SellerProfileService sellerProfileService;

    @BeforeEach
    void setUp() {
        sellerProfileService = mock(SellerProfileService.class);
        SellerProfileController controller = new SellerProfileController(sellerProfileService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("판매자 프로필을 등록하면 201 상태를 반환한다")
    void create() throws Exception {
        SellerProfileResponse response =
                new SellerProfileResponse(100L, 1L, "판매자 소개", null, null, null);

        when(sellerProfileService.create(eq(1L), any(SellerProfileCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/seller-profiles")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "판매자 소개"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.description").value("판매자 소개"));
    }

    @Test
    @DisplayName("사용자 ID 헤더가 없으면 400 상태를 반환한다")
    void rejectMissingUserId() throws Exception {
        mockMvc.perform(post("/seller-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "판매자 소개"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sellerProfileService);
    }

    @Test
    @DisplayName("일반 사용자는 프로필 등록 시 403 오류 응답을 반환한다")
    void rejectNonSeller() throws Exception {
        when(sellerProfileService.create(eq(1L), any(SellerProfileCreateRequest.class)))
                .thenThrow(new ServiceException(SellerProfileErrorCode.SELLER_ROLE_REQUIRED));

        mockMvc.perform(post("/seller-profiles")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "판매자 소개"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_REQUIRED"));
    }

    @Test
    @DisplayName("중복 판매자 프로필 등록 시 409 오류 응답을 반환한다")
    void rejectDuplicateProfile() throws Exception {
        when(sellerProfileService.create(eq(1L), any(SellerProfileCreateRequest.class)))
                .thenThrow(new ServiceException(
                        SellerProfileErrorCode.SELLER_PROFILE_ALREADY_EXISTS));

        mockMvc.perform(post("/seller-profiles")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "판매자 소개"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("내 판매자 프로필을 조회하면 200 상태를 반환한다")
    void getMine() throws Exception {
        when(sellerProfileService.getMine(1L))
                .thenReturn(new SellerProfileResponse(
                        100L, 1L, "판매자 소개", null, null, null));

        mockMvc.perform(get("/seller-profiles/me")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L));
    }

    @Test
    @DisplayName("판매자 프로필 ID로 공개 프로필을 조회할 수 있다")
    void getById() throws Exception {
        when(sellerProfileService.getById(100L))
                .thenReturn(new SellerProfileResponse(
                        100L, 1L, "판매자 소개", null, null, null));

        mockMvc.perform(get("/seller-profiles/{sellerProfileId}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L));
    }

    @Test
    @DisplayName("판매자 프로필을 수정하면 200 상태를 반환한다")
    void update() throws Exception {
        when(sellerProfileService.update(eq(1L), any(SellerProfileUpdateRequest.class)))
                .thenReturn(new SellerProfileResponse(
                        100L, 1L, "수정된 소개", null, null, null));

        mockMvc.perform(patch("/seller-profiles/me")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "수정된 소개"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("수정된 소개"));
    }

    @Test
    @DisplayName("판매자 프로필을 삭제하면 204 상태를 반환한다")
    void deleteProfile() throws Exception {
        mockMvc.perform(delete("/seller-profiles/me")
                        .header("X-User-Id", "1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(sellerProfileService).delete(1L);
    }
}
