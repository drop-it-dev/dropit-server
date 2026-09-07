package com.dropit.global.security.jwt;

public record JwtTokenPair(
        String accessToken,
        String refreshToken
) {
}