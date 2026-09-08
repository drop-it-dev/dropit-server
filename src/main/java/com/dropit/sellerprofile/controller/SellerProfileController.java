package com.dropit.sellerprofile.controller;

import com.dropit.global.security.principal.CurrentUserId;
import com.dropit.sellerprofile.dto.request.SellerProfileCreateRequest;
import com.dropit.sellerprofile.dto.request.SellerProfileUpdateRequest;
import com.dropit.sellerprofile.dto.response.SellerProfileResponse;
import com.dropit.sellerprofile.service.SellerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/seller-profiles")
public class SellerProfileController {

    private final SellerProfileService sellerProfileService;

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<SellerProfileResponse> create(
            @CurrentUserId Long userId,
            @Valid @RequestBody SellerProfileCreateRequest request
    ) {
        SellerProfileResponse response =
                sellerProfileService.create(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<SellerProfileResponse> getMine(
            @CurrentUserId Long userId
    ) {
        SellerProfileResponse response =
                sellerProfileService.getMine(userId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sellerProfileId}")
    public ResponseEntity<SellerProfileResponse> getById(
            @PathVariable Long sellerProfileId
    ) {
        SellerProfileResponse response =
                sellerProfileService.getById(sellerProfileId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<SellerProfileResponse> update(
            @CurrentUserId Long userId,
            @Valid @RequestBody SellerProfileUpdateRequest request
    ) {
        SellerProfileResponse response =
                sellerProfileService.update(userId, request);

        return ResponseEntity.ok(response);
    }

    @PutMapping(
            value = "/me/image",
            consumes = "multipart/form-data"
    )
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<SellerProfileResponse> uploadImage(
            @CurrentUserId Long userId,
            @RequestParam("file") MultipartFile file
    ) {
        SellerProfileResponse response =
                sellerProfileService.uploadImage(userId, file);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<Void> delete(
            @CurrentUserId Long userId
    ) {
        sellerProfileService.delete(userId);

        return ResponseEntity.noContent().build();
    }
}