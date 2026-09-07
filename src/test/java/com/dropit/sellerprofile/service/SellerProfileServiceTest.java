package com.dropit.sellerprofile.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.sellerprofile.dto.request.SellerProfileCreateRequest;
import com.dropit.sellerprofile.dto.request.SellerProfileUpdateRequest;
import com.dropit.sellerprofile.dto.response.SellerProfileResponse;
import com.dropit.sellerprofile.entity.SellerProfile;
import com.dropit.sellerprofile.exception.SellerProfileErrorCode;
import com.dropit.sellerprofile.repository.SellerProfileRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerProfileServiceTest {

    @Mock SellerProfileRepository sellerProfileRepository;
    @Mock UserRepository userRepository;
    @InjectMocks SellerProfileService sellerProfileService;

    @Test
    @DisplayName("판매자는 판매자 프로필을 등록할 수 있다")
    void createSellerProfile() {
        Long userId = 1L;
        User seller = createUser(UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", userId);
        SellerProfileCreateRequest request = createRequest();

        when(userRepository.findById(userId)).thenReturn(Optional.of(seller));
        when(sellerProfileRepository.existsByUser_Id(userId)).thenReturn(false);
        when(sellerProfileRepository.save(any(SellerProfile.class))).thenAnswer(invocation -> {
            SellerProfile profile = invocation.getArgument(0);
            ReflectionTestUtils.setField(profile, "id", 100L);
            return profile;
        });

        SellerProfileResponse response = sellerProfileService.create(userId, request);

        assertEquals(100L, response.id());
        assertEquals(userId, response.userId());
        assertEquals("판매자 소개", response.description());
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 판매자 프로필을 등록할 수 없다")
    void rejectMissingUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertServiceException(
                SellerProfileErrorCode.USER_NOT_FOUND,
                () -> sellerProfileService.create(1L, createRequest())
        );

        verify(sellerProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("일반 사용자는 판매자 프로필을 등록할 수 없다")
    void rejectNonSeller() {
        User user = createUser(UserRole.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertServiceException(
                SellerProfileErrorCode.SELLER_ROLE_REQUIRED,
                () -> sellerProfileService.create(1L, createRequest())
        );

        verify(sellerProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("판매자 프로필은 중복 등록할 수 없다")
    void rejectDuplicateProfile() {
        User seller = createUser(UserRole.SELLER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(sellerProfileRepository.existsByUser_Id(1L)).thenReturn(true);

        assertServiceException(
                SellerProfileErrorCode.SELLER_PROFILE_ALREADY_EXISTS,
                () -> sellerProfileService.create(1L, createRequest())
        );

        verify(sellerProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("내 판매자 프로필을 조회할 수 있다")
    void getMine() {
        User seller = createUser(UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", 1L);
        SellerProfile profile = createProfile(seller);
        ReflectionTestUtils.setField(profile, "id", 100L);
        when(sellerProfileRepository.findByUser_Id(1L)).thenReturn(Optional.of(profile));

        SellerProfileResponse response = sellerProfileService.getMine(1L);

        assertEquals(100L, response.id());
        assertEquals(1L, response.userId());
    }

    @Test
    @DisplayName("존재하지 않는 판매자 프로필은 조회할 수 없다")
    void rejectMissingProfile() {
        when(sellerProfileRepository.findByUser_Id(1L)).thenReturn(Optional.empty());

        assertServiceException(
                SellerProfileErrorCode.SELLER_PROFILE_NOT_FOUND,
                () -> sellerProfileService.getMine(1L)
        );
    }

    @Test
    @DisplayName("판매자 프로필 ID로 조회할 수 있다")
    void getById() {
        User seller = createUser(UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", 1L);
        SellerProfile profile = createProfile(seller);
        ReflectionTestUtils.setField(profile, "id", 100L);
        when(sellerProfileRepository.findById(100L)).thenReturn(Optional.of(profile));

        SellerProfileResponse response = sellerProfileService.getById(100L);

        assertEquals(100L, response.id());
        assertEquals(1L, response.userId());
    }

    @Test
    @DisplayName("판매자 프로필을 수정할 수 있다")
    void updateSellerProfile() {
        User seller = createUser(UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", 1L);
        SellerProfile profile = createProfile(seller);
        when(sellerProfileRepository.findByUser_Id(1L)).thenReturn(Optional.of(profile));

        SellerProfileUpdateRequest request = new SellerProfileUpdateRequest(
                "수정된 소개", "updated-image", "updated-instagram", "updated-youtube"
        );

        SellerProfileResponse response = sellerProfileService.update(1L, request);

        assertEquals("수정된 소개", profile.getDescription());
        assertEquals("수정된 소개", response.description());
        verify(sellerProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("판매자 프로필을 삭제할 수 있다")
    void deleteSellerProfile() {
        User seller = createUser(UserRole.SELLER);
        SellerProfile profile = createProfile(seller);
        when(sellerProfileRepository.findByUser_Id(1L)).thenReturn(Optional.of(profile));

        sellerProfileService.delete(1L);

        verify(sellerProfileRepository).delete(profile);
    }

    private SellerProfileCreateRequest createRequest() {
        return new SellerProfileCreateRequest("판매자 소개", null, null, null);
    }

    private SellerProfile createProfile(User user) {
        return new SellerProfile(user, "판매자 소개", null, null, null);
    }

    private User createUser(UserRole role) {
        return new User("seller@example.com", "encoded-password", "seller", role);
    }

    private void assertServiceException(SellerProfileErrorCode errorCode, Executable executable) {
        ServiceException exception = assertThrows(ServiceException.class, executable);
        assertEquals(errorCode, exception.getErrorCode());
    }
}
