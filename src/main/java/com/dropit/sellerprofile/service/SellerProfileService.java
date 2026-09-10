package com.dropit.sellerprofile.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.global.storage.S3ImageService;
import com.dropit.sellerprofile.dto.request.SellerProfileCreateRequest;
import com.dropit.sellerprofile.dto.request.SellerProfileUpdateRequest;
import com.dropit.sellerprofile.dto.response.SellerProfileResponse;
import com.dropit.sellerprofile.entity.SellerProfile;
import com.dropit.sellerprofile.exception.SellerProfileErrorCode;
import com.dropit.sellerprofile.repository.SellerProfileRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerProfileService {

    private final SellerProfileRepository sellerProfileRepository;
    private final UserRepository userRepository;
    private final S3ImageService s3ImageService;

    @Transactional
    public SellerProfileResponse create(
            Long userId,
            SellerProfileCreateRequest request
    ) {
        User user = findUser(userId);

        if (user.getRole() != UserRole.SELLER) {
            throw new ServiceException(
                    SellerProfileErrorCode.SELLER_ROLE_REQUIRED
            );
        }

        if (sellerProfileRepository.existsByUser_Id(userId)) {
            throw new ServiceException(
                    SellerProfileErrorCode.SELLER_PROFILE_ALREADY_EXISTS
            );
        }

        /*
         * The profile is initially created without an image.
         * The image must be uploaded through PUT /seller-profiles/me/image.
         */
        SellerProfile sellerProfile = new SellerProfile(
                user,
                request.description(),
                null,
                request.instagramUrl(),
                request.youtubeUrl()
        );

        SellerProfile savedProfile =
                sellerProfileRepository.save(sellerProfile);

        return toResponse(savedProfile);
    }

    public SellerProfileResponse getMine(Long userId) {
        SellerProfile sellerProfile = findByUserId(userId);

        return toResponse(sellerProfile);
    }

    public SellerProfileResponse getById(Long sellerProfileId) {
        SellerProfile sellerProfile =
                sellerProfileRepository.findById(sellerProfileId)
                        .orElseThrow(() ->
                                new ServiceException(
                                        SellerProfileErrorCode
                                                .SELLER_PROFILE_NOT_FOUND
                                )
                        );

        return toResponse(sellerProfile);
    }

    @Transactional
    public SellerProfileResponse update(
            Long userId,
            SellerProfileUpdateRequest request
    ) {
        SellerProfile sellerProfile = findByUserId(userId);

        /*
         * Preserve the current S3 image key.
         * This endpoint updates only the profile information.
         */
        sellerProfile.update(
                request.description(),
                sellerProfile.getImageUrl(),
                request.instagramUrl(),
                request.youtubeUrl()
        );

        return toResponse(sellerProfile);
    }

    @Transactional
    public SellerProfileResponse uploadImage(
            Long userId,
            MultipartFile file
    ) {
        SellerProfile sellerProfile = findByUserId(userId);

        if (sellerProfile.getUser().getRole() != UserRole.SELLER) {
            throw new ServiceException(
                    SellerProfileErrorCode.SELLER_ROLE_REQUIRED
            );
        }

        String oldKey = sellerProfile.getImageUrl();

        String newKey = s3ImageService.upload(
                file,
                "seller-profiles/" + userId
        );

        sellerProfile.changeImage(newKey);

        synchronizeImageReplacement(oldKey, newKey);

        return toResponse(sellerProfile);
    }

    @Transactional
    public void delete(Long userId) {
        SellerProfile sellerProfile = findByUserId(userId);
        String imageKey = sellerProfile.getImageUrl();

        sellerProfileRepository.delete(sellerProfile);

        deleteImageAfterCommit(imageKey);
    }

    @Transactional
    public void deleteImage(Long userId) {
        SellerProfile sellerProfile = findByUserId(userId);

        String imageKey = sellerProfile.getImageUrl();
        sellerProfile.changeImage(null);
        deleteImageAfterCommit(imageKey);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ServiceException(
                                SellerProfileErrorCode.USER_NOT_FOUND
                        )
                );
    }

    private SellerProfile findByUserId(Long userId) {
        return sellerProfileRepository.findByUser_Id(userId)
                .orElseThrow(() ->
                        new ServiceException(
                                SellerProfileErrorCode
                                        .SELLER_PROFILE_NOT_FOUND
                        )
                );
    }

    /*
     * The entity stores the private S3 object key.
     * The response receives a temporary presigned URL.
     */
    private SellerProfileResponse toResponse(
            SellerProfile sellerProfile
    ) {
        String imageUrl = s3ImageService.createDownloadUrl(
                sellerProfile.getImageUrl()
        );

        return SellerProfileResponse.from(
                sellerProfile,
                imageUrl
        );
    }

    /*
     * Delete the old image only if the database transaction succeeds.
     *
     * If the database transaction fails, delete the newly uploaded image
     * because the database will still reference the old image.
     */
    private void synchronizeImageReplacement(
            String oldKey,
            String newKey
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        s3ImageService.deleteQuietly(oldKey);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            s3ImageService.deleteQuietly(newKey);
                        }
                    }
                }
        );
    }

    private void deleteImageAfterCommit(String imageKey) {
        if (imageKey == null) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        s3ImageService.deleteQuietly(imageKey);
                    }
                }
        );
    }
}