package com.dropit.drop.controller;

import com.dropit.drop.dto.request.DropCreateRequest;
import com.dropit.drop.dto.request.DropUpdateRequest;
import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.dto.response.DropStockResponse;
import com.dropit.drop.service.DropService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class DropController {

    private final DropService dropService;

    @PostMapping("/drops")
    public ResponseEntity<Long> create(
            @RequestParam Long userId, // TODO: 인증 기능 적용 후 SecurityContext의 로그인 사용자 정보에서 추출
            @Valid @RequestBody DropCreateRequest request
    ) {
        Long dropId = dropService.save(userId, request);

        return ResponseEntity
                .created(URI.create("/drops/" + dropId))
                .body(dropId);
    }

    @GetMapping("/drops")
    public ResponseEntity<List<DropResponse>> getAll() {
        return ResponseEntity.ok(dropService.getAll());
    }

    @GetMapping("/drops/{dropId}")
    public ResponseEntity<DropResponse> getOne(
            @PathVariable Long dropId
    ) {
        return ResponseEntity.ok(dropService.getOne(dropId));
    }
}
