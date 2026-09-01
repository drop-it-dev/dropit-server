package com.dropit.user.controller;

import com.dropit.user.dto.request.PasswordUpdateRequest;
import com.dropit.user.dto.request.UserUpdateRequest;
import com.dropit.user.dto.response.UserResponse;
import com.dropit.user.entity.User;
import com.dropit.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserController {
    // Temporary endpoints, Authentication, Authorization, JWT need to be added
    private final UserService userService;

    @GetMapping("/users/me")
    public UserResponse getMyInfo(
            @RequestHeader("X-User-Id") Long userId
    ) {
        User user = userService.findUser(userId);

        return UserResponse.from(user);
    }

    @PatchMapping("/users/me")
    public UserResponse updateMyProfile(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        User user = userService.updateProfile(userId, request);

        return UserResponse.from(user);
    }

    @PatchMapping("/users/me/password")
    public ResponseEntity<Void> updateMyPassword(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PasswordUpdateRequest request
    ) {
        userService.updatePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/me")
    public ResponseEntity<Void> deleteMyAccount(
            @RequestHeader("X-User-Id") Long userId
    ) {
        userService.deleteUser(userId);

        return ResponseEntity.noContent().build();
    }

}
