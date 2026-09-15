package com.dropit.auth.dto.request;

import com.dropit.user.entity.UserRole;

public enum SignupRole {
    USER,
    SELLER;

    public UserRole toUserRole() {
        return switch (this) {
            case USER -> UserRole.USER;
            case SELLER -> UserRole.SELLER;
        };
    }
}