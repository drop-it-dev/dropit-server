package com.dropit.global.security.jwt;

import com.dropit.global.exception.ServiceException;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
    );

    private JwtUtil jwtUtil;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                SECRET,
                new JwtProperties.Expire(Duration.ofMinutes(30), Duration.ofDays(14))
        );
        jwtUtil = new JwtUtil(properties);

        user = new User("seller@example.com", "encoded-password", "seller", UserRole.SELLER);
        ReflectionTestUtils.setField(user, "id", 42L);
    }

    @Test
    void issueTokenPairCreatesAccessTokenWithUserIdAndRole() {
        JwtTokenPair tokenPair = jwtUtil.issueTokenPair(user);

        Claims claims = jwtUtil.getAccessTokenClaims(tokenPair.accessToken());

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get(JwtUtil.TOKEN_TYPE_CLAIM, String.class))
                .isEqualTo(JwtUtil.TOKEN_TYPE_ACCESS);
        assertThat(claims.get(JwtUtil.USER_ROLE_CLAIM, String.class))
                .isEqualTo(UserRole.SELLER.name());
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuedAt()).isBefore(claims.getExpiration());
    }

    @Test
    void issueTokenPairCreatesRefreshTokenWithoutRole() {
        JwtTokenPair tokenPair = jwtUtil.issueTokenPair(user);

        Claims claims = jwtUtil.getRefreshTokenClaims(tokenPair.refreshToken());

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get(JwtUtil.TOKEN_TYPE_CLAIM, String.class))
                .isEqualTo(JwtUtil.TOKEN_TYPE_REFRESH);
        assertThat(claims.get(JwtUtil.USER_ROLE_CLAIM)).isNull();
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        JwtTokenPair tokenPair = jwtUtil.issueTokenPair(user);

        assertThatThrownBy(() -> jwtUtil.getAccessTokenClaims(tokenPair.refreshToken()))
                .isInstanceOfSatisfying(ServiceException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(JwtErrorCode.JWT_INVALID_TOKEN_TYPE)
                );
    }
}
