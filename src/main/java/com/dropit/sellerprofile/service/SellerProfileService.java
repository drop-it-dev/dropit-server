package com.dropit.sellerprofile.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.sellerprofile.dto.request.SellerProfileCreateRequest;
import com.dropit.sellerprofile.dto.request.SellerProfileUpdateRequest;
import com.dropit.sellerprofile.dto.response.SellerProfileResponse;
import com.dropit.sellerprofile.entity.SellerProfile;
import com.dropit.sellerprofile.exception.SellerProfileErrorCode;
import com.dropit.sellerprofile.repository.SellerProfileRepository;
import com.dropit.user.entity.User;
import com.dropit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerProfileService {

    private final SellerProfileRepository sellerProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public SellerProfileResponse create(Long userId, SellerProfileCreateRequest request) {
        User user = findUser(userId);

        if (sellerProfileRepository.existsByUser_Id(userId)) {
            throw new ServiceException(
                    SellerProfileErrorCode.SELLER_PROFILE_ALREADY_EXISTS
            );
        }

        SellerProfile sellerProfile = new SellerProfile(
                user,
                request.description(),
                request.imageUrl(),
                request.instagramUrl(),
                request.youtubeUrl()
        );

        return SellerProfileResponse.from(
                sellerProfileRepository.save(sellerProfile)
        );
    }

    public SellerProfileResponse getMine(Long userId) {
        return SellerProfileResponse.from(findByUserId(userId));
    }

    public SellerProfileResponse getById(Long sellerProfileId) {
        SellerProfile sellerProfile = sellerProfileRepository.findById(sellerProfileId)
                .orElseThrow(() -> new ServiceException(
                        SellerProfileErrorCode.SELLER_PROFILE_NOT_FOUND
                ));

        return SellerProfileResponse.from(sellerProfile);
    }

    @Transactional
    public SellerProfileResponse update(
            Long userId,
            SellerProfileUpdateRequest request
    ) {
        SellerProfile sellerProfile = findByUserId(userId);

        sellerProfile.update(
                request.description(),
                request.imageUrl(),
                request.instagramUrl(),
                request.youtubeUrl()
        );

        return SellerProfileResponse.from(sellerProfile);
    }

    @Transactional
    public void delete(Long userId) {
        SellerProfile sellerProfile = findByUserId(userId);
        sellerProfileRepository.delete(sellerProfile);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(
                        SellerProfileErrorCode.USER_NOT_FOUND
                ));
    }

    private SellerProfile findByUserId(Long userId) {
        return sellerProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ServiceException(
                        SellerProfileErrorCode.SELLER_PROFILE_NOT_FOUND
                ));
    }
}
