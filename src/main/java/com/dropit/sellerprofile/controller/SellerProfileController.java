package com.dropit.sellerprofile.controller;

import com.dropit.sellerprofile.dto.request.SellerProfileCreateRequest;
import com.dropit.sellerprofile.dto.request.SellerProfileUpdateRequest;
import com.dropit.sellerprofile.dto.response.SellerProfileResponse;
import com.dropit.sellerprofile.service.SellerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/seller-profiles")
public class SellerProfileController {

    private final SellerProfileService sellerProfileService;

    @PostMapping
    public ResponseEntity<SellerProfileResponse> create(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody SellerProfileCreateRequest request
    ) {
        SellerProfileResponse response = sellerProfileService.create(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<SellerProfileResponse> getMine(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(sellerProfileService.getMine(userId));
    }

    @GetMapping("/{sellerProfileId}")
    public ResponseEntity<SellerProfileResponse> getById(
            @PathVariable Long sellerProfileId
    ) {
        return ResponseEntity.ok(sellerProfileService.getById(sellerProfileId));
    }

    @PatchMapping("/me")
    public ResponseEntity<SellerProfileResponse> update(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody SellerProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(sellerProfileService.update(userId, request));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") Long userId
    ) {
        sellerProfileService.delete(userId);

        return ResponseEntity.noContent().build();
    }
}
