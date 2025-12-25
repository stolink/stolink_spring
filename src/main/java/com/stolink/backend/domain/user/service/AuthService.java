package com.stolink.backend.domain.user.service;

import com.stolink.backend.domain.user.dto.LoginRequest;
import com.stolink.backend.domain.user.dto.RegisterRequest;
import com.stolink.backend.domain.user.dto.UserResponse;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        // For simplicity, we're not hashing password (you mentioned no JWT/Security)
        // In production, you should hash the password
        User user = User.builder()
                .email(request.getEmail())
                .password(request.getPassword()) // Should be hashed
                .nickname(request.getNickname())
                .build();

        user = userRepository.save(user);
        log.info("User registered: {}", user.getEmail());

        return UserResponse.from(user);
    }

    public UserResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        // Simple password check (in production, use proper password hashing)
        if (!user.getPassword().equals(request.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        log.info("User logged in: {}", user.getEmail());
        return UserResponse.from(user);
    }

    public UserResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, String nickname, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        user.updateProfile(nickname, avatarUrl);
        return UserResponse.from(user);
    }
}
