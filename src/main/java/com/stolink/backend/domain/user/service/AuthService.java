package com.stolink.backend.domain.user.service;

import com.stolink.backend.domain.user.dto.*;
import com.stolink.backend.domain.user.entity.AuthProvider;
import com.stolink.backend.domain.user.entity.RefreshToken;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.RefreshTokenRepository;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import com.stolink.backend.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 일반 회원가입
     */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        // BCrypt로 비밀번호 해싱
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .provider(AuthProvider.LOCAL)
                .build();

        user = userRepository.save(user);
        log.info("User registered: {}", user.getEmail());

        return generateTokenResponse(user);
    }

    /**
     * 일반 로그인
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        // OAuth2 사용자인 경우 일반 로그인 불가
        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new IllegalArgumentException("해당 이메일은 " + user.getProvider() + " 로그인을 사용해주세요.");
        }

        // BCrypt로 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        log.info("User logged in: {}", user.getEmail());
        return generateTokenResponse(user);
    }

    /**
     * 토큰 갱신 (RDB에서 Refresh Token 검증)
     */
    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String refreshTokenStr = request.getRefreshToken();

        // 1. RDB에서 Refresh Token 조회
        log.debug("Attempting to refresh token: {}", refreshTokenStr);
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 refresh token입니다."));

        // 2. 만료 여부 확인
        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new IllegalArgumentException("만료된 refresh token입니다. 다시 로그인해주세요.");
        }

        User user = storedToken.getUser();

        // 3. 기존 토큰 삭제 (Token Rotation - 보안 강화)
        refreshTokenRepository.delete(storedToken);

        log.info("Token refreshed for user: {}", user.getEmail());
        return generateTokenResponse(user);
    }

    /**
     * 로그아웃 (현재 토큰 무효화)
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
        log.info("User logged out, refresh token invalidated");
    }

    /**
     * 전체 로그아웃 (모든 디바이스에서 로그아웃)
     */
    @Transactional
    public void logoutAll(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        refreshTokenRepository.deleteAllByUser(user);
        log.info("User {} logged out from all devices", user.getEmail());
    }

    /**
     * 현재 사용자 정보 조회
     */
    public UserResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return UserResponse.from(user);
    }

    /**
     * 사용자 프로필 업데이트
     */
    @Transactional
    public UserResponse updateUser(UUID userId, String nickname, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        user.updateProfile(nickname, avatarUrl);
        return UserResponse.from(user);
    }

    /**
     * OAuth2 전용: 생성된 Refresh Token을 RDB에 저장
     */
    @Transactional
    public void saveRefreshToken(UUID userId, String refreshTokenStr) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(
                jwtTokenProvider.getRefreshTokenExpirySeconds());

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenStr)
                .user(user)
                .expiresAt(expiresAt)
                .build();

        refreshTokenRepository.save(refreshToken);
    }

    /**
     * JWT 토큰 응답 생성 (Refresh Token을 RDB에 저장)
     */
    private TokenResponse generateTokenResponse(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshTokenStr = jwtTokenProvider.createRefreshToken(user.getId());
        long expiresIn = jwtTokenProvider.getAccessTokenExpirySeconds();

        // 공통 로직 호출
        saveRefreshToken(user.getId(), refreshTokenStr);

        return TokenResponse.of(accessToken, refreshTokenStr, expiresIn, user);
    }
}
