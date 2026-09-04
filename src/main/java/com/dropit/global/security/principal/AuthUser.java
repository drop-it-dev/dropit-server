package com.dropit.global.security.principal;

import org.springframework.security.core.AuthenticatedPrincipal;

public record AuthUser(Long id) implements AuthenticatedPrincipal {
    @Override
    public String getName() {
        return id.toString();
    }
}
