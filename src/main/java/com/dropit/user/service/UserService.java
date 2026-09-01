package com.dropit.user.service;

import com.dropit.global.exception.ServiceException;
import com.dropit.user.dto.request.PasswordUpdateRequest;
import com.dropit.user.dto.request.UserUpdateRequest;
import com.dropit.user.entity.User;
import com.dropit.user.exception.UserErrorCode;
import com.dropit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ServiceException(UserErrorCode.USER_NOT_FOUND)
                );
    }

    @Transactional
    public User updateProfile(
            Long userId,
            UserUpdateRequest request
    ) {
        User user = findUser(userId);

        if (request.email() != null
                && !Objects.equals(user.getEmail(), request.email())) {

            if (userRepository.existsByEmail(request.email())) {
                throw new ServiceException(
                        UserErrorCode.EMAIL_ALREADY_EXISTS
                );
            }

            user.updateEmail(request.email());
        }

        if (request.username() != null
                && !Objects.equals(user.getUsername(), request.username())) {

            if (userRepository.existsByUsername(request.username())) {
                throw new ServiceException(
                        UserErrorCode.USERNAME_ALREADY_EXISTS
                );
            }

            user.updateUsername(request.username());
        }

        return user;
    }

    @Transactional
    public void updatePassword(
            Long userId,
            PasswordUpdateRequest request
    ) {
        User user = findUser(userId);

        if (!Objects.equals(
                user.getPassword(),
                request.currentPassword()
        )) {
            throw new ServiceException(
                    UserErrorCode.INVALID_CURRENT_PASSWORD
            );
        }

        user.updatePassword(request.newPassword());
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = findUser(userId);

        userRepository.delete(user);
    }
}