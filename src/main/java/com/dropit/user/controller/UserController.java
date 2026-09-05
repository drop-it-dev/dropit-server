package com.dropit.user.controller;

import com.dropit.global.security.principal.CurrentUserId;
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

    private final UserService userService;

    @GetMapping("/users/me")
    public UserResponse getMyInfo(
            @CurrentUserId Long userId
    ) {
        User user = userService.findUser(userId);

        return UserResponse.from(user);
    }

    @PatchMapping("/users/me")
    public UserResponse updateMyProfile(
            @CurrentUserId Long userId,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        User user =
                userService.updateProfile(userId, request);

        return UserResponse.from(user);
    }

    @PatchMapping("/users/me/password")
    public ResponseEntity<Void> updateMyPassword(
            @CurrentUserId Long userId,
            @Valid @RequestBody PasswordUpdateRequest request
    ) {
        userService.updatePassword(userId, request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/me")
    public ResponseEntity<Void> deleteMyAccount(
            @CurrentUserId Long userId
    ) {
        userService.deleteUser(userId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUserByAdmin(
            @PathVariable Long userId
    ) {
        userService.deleteUserByAdmin(userId);

        return ResponseEntity.noContent().build();
    }
}
