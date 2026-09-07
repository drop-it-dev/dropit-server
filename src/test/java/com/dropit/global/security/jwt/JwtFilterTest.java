package com.dropit.global.security.jwt;

import com.dropit.global.exception.ServiceException;
import com.dropit.global.security.SecurityErrorResponseSender;
import com.dropit.global.security.principal.AuthUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final SecurityErrorResponseSender errorResponseSender = mock(SecurityErrorResponseSender.class);
    private final JwtFilter jwtFilter = new JwtFilter(jwtUtil, errorResponseSender);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestWithoutBearerTokenContinuesWithoutAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validAccessTokenCreatesAuthenticatedSecurityContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("42");
        when(claims.get(JwtUtil.USER_ROLE_CLAIM, String.class)).thenReturn("SELLER");
        when(jwtUtil.getAccessTokenClaims("valid-token")).thenReturn(claims);

        jwtFilter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthUser(42L));
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SELLER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidTokenWritesErrorAndStopsFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        ServiceException exception = new ServiceException(JwtErrorCode.JWT_TOKEN_ERROR);
        when(jwtUtil.getAccessTokenClaims("invalid-token")).thenThrow(exception);

        jwtFilter.doFilter(request, response, filterChain);

        verify(errorResponseSender).send(response, exception);
        verify(filterChain, never()).doFilter(request, response);
    }
}