package com.dropit.drop.controller;

import com.dropit.drop.dto.request.DropCreateRequest;
import com.dropit.drop.dto.request.DropSearchCondition;
import com.dropit.drop.dto.request.DropUpdateRequest;
import com.dropit.drop.dto.request.DropVisibilityUpdateRequest;
import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.service.DropService;
import com.dropit.global.security.principal.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class DropController {

    private final DropService dropService;

    @PostMapping("/drops")
    public ResponseEntity<Long> create(
            @CurrentUserId Long sellerId,
            @Valid @RequestBody DropCreateRequest request
    ) {
        Long dropId = dropService.save(sellerId, request);

        return ResponseEntity
                .created(URI.create("/drops/" + dropId))
                .body(dropId);
    }

    @GetMapping("/drops")
    public ResponseEntity<Page<DropResponse>> getAll(
            @ModelAttribute DropSearchCondition condition,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<DropResponse> response = dropService.getAll(condition, pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/drops/{dropId}")
    public ResponseEntity<DropResponse> getOne(
            @PathVariable Long dropId
    ) {
        return ResponseEntity.ok(dropService.getOne(dropId));
    }

    @GetMapping("/creators/{sellerId}/drops")
    public ResponseEntity<Page<DropResponse>> getPublicDropsBySeller(
            @PathVariable Long sellerId,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        Page<DropResponse> response = dropService.getPublicDropsBySeller(
                sellerId,
                pageable
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/me/drops")
    public ResponseEntity<Page<DropResponse>> getDropsForSellerManagement(
            @CurrentUserId Long sellerId,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        Page<DropResponse> response = dropService.getDropsForSellerManagement(
                sellerId,
                pageable
        );

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/drops/{dropId}")
    public ResponseEntity<DropResponse> update(
            @CurrentUserId Long sellerId,
            @PathVariable Long dropId,
            @Valid @RequestBody DropUpdateRequest request
    ) {
        return ResponseEntity.ok(dropService.update(sellerId, dropId, request));
    }

    @PatchMapping("/drops/{dropId}/visibility")
    public ResponseEntity<DropResponse> changeVisibility(
            @CurrentUserId Long sellerId,
            @PathVariable Long dropId,
            @Valid @RequestBody DropVisibilityUpdateRequest request
    ) {
        return ResponseEntity.ok(dropService.changeVisibility(sellerId, dropId, request));
    }

    @DeleteMapping("/drops/{dropId}")
    public ResponseEntity<Void> delete(
            @CurrentUserId Long sellerId,
            @PathVariable Long dropId
    ) {
        dropService.delete(sellerId, dropId);

        return ResponseEntity.noContent().build();
    }
}
