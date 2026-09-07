package com.dropit.global.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        Expire expire
) {
    public record Expire(
            Duration access,
            Duration refresh
    ) {
    }
}