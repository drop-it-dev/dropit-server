package com.dropit.global.security.jwt;

import com.dropit.global.exception.ServiceException;
import com.dropit.global.security.SecurityErrorResponseSender;
import com.dropit.global.security.authentication.JwtAuthenticationToken;
import com.dropit.global.security.principal.AuthUser;
import com.dropit.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final SecurityErrorResponseSender errorResponseSender;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");

        // Bearer 토큰이 없는 요청의 허용 여부는 SecurityConfig가 판단한다.
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring("Bearer ".length());

        try {
            authenticate(token, request);
        } catch (ServiceException exception) {
            errorResponseSender.send(response, exception);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        Claims claims = jwtUtil.getAccessTokenClaims(token);
        Long userId = parseUserId(claims.getSubject());
        UserRole role = parseRole(claims.get(JwtUtil.USER_ROLE_CLAIM, String.class));

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                new AuthUser(userId), // principal
                List.of(new SimpleGrantedAuthority(role.authority())) // authorities
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw JwtErrorCode.JWT_TOKEN_ERROR.toException();
        }
    }

    private UserRole parseRole(String roleClaim) {
        try {
            return UserRole.valueOf(roleClaim);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw JwtErrorCode.JWT_TOKEN_ERROR.toException();
        }
    }
}

