package com.dropit.global.security.principal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(
        expression = "id",
        errorOnInvalidType = true
)
public @interface CurrentUserId {
}