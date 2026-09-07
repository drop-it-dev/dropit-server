package com.dropit.user.dto.request;

import com.dropit.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(

        @NotNull(message = "사용자 역할은 필수입니다.")
        UserRole role

) {
}
