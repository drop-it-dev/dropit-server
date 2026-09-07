package com.dropit.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest (
    @Email(message = "올바른 이메일 형식이어야 합니다." )
    String email,
    @Size(min = 1, max = 100, message = "사용자 이름은 1자 이상 100자 이하여야 합니다.")
    String username
){

}
