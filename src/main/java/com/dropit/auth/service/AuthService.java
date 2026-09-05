package com.dropit.auth.service;

import com.dropit.auth.dto.request.LoginRequest;
import com.dropit.auth.dto.request.ReissueRequest;
import com.dropit.auth.dto.request.SignupRequest;
import com.dropit.auth.dto.response.TokenResponse;
import com.dropit.auth.exception.AuthErrorCode;
import com.dropit.global.exception.ServiceException;
import com.dropit.global.security.jwt.JwtTokenPair;
import com.dropit.global.security.jwt.JwtUtil;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.exception.UserErrorCode;
import com.dropit.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public void signup(SignupRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new ServiceException(
                    UserErrorCode.EMAIL_ALREADY_EXISTS
            );
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new ServiceException(
                    UserErrorCode.USERNAME_ALREADY_EXISTS
            );
        }

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.username(),
                UserRole.USER
        );

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {

        User user = userRepository
                .findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() ->
                        new ServiceException(
                                AuthErrorCode.INVALID_CREDENTIALS
                        )
                );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new ServiceException(
                    AuthErrorCode.INVALID_CREDENTIALS
            );
        }

        JwtTokenPair tokenPair =
                jwtUtil.issueTokenPair(user);

        return TokenResponse.from(tokenPair);
    }

    @Transactional(readOnly = true)
    public TokenResponse reissue(ReissueRequest request) {

        Claims claims =
                jwtUtil.getRefreshTokenClaims(
                        request.refreshToken()
                );

        Long userId = parseUserId(
                claims.getSubject()
        );

        User user = userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() ->
                        new ServiceException(
                                AuthErrorCode.INVALID_REFRESH_TOKEN
                        )
                );

        JwtTokenPair tokenPair =
                jwtUtil.issueTokenPair(user);

        return TokenResponse.from(tokenPair);
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException e) {
            throw new ServiceException(
                    AuthErrorCode.INVALID_REFRESH_TOKEN
            );
        }
    }
}