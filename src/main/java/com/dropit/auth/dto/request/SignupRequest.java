package com.dropit.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이어야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 255, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "사용자 이름은 필수입니다.")
        @Size(min = 1, max = 100, message = "사용자 이름은 1자 이상 100자 이하여야 합니다.")
        String username
) {
}