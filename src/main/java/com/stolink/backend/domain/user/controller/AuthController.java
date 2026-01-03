package com.stolink.backend.domain.user.controller;

import com.stolink.backend.domain.user.dto.*;
import com.stolink.backend.domain.user.service.AuthService;
import com.stolink.backend.global.common.dto.ApiResponse;
import com.stolink.backend.global.util.CookieUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieUtils cookieUtils;

    /**
     * 일반 회원가입
     * 토큰은 HttpOnly 쿠키로 전달
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<LoginResponse>> register(@RequestBody RegisterRequest request) {
        TokenResponse token = authService.register(request);

        ResponseCookie accessCookie = cookieUtils.createAccessTokenCookie(token.getAccessToken());
        ResponseCookie refreshCookie = cookieUtils.createRefreshTokenCookie(token.getRefreshToken());

        LoginResponse response = LoginResponse.of(token.getExpiresIn(), token.getUser());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.created(response));
    }

    /**
     * 일반 로그인
     * 토큰은 HttpOnly 쿠키로 전달
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        TokenResponse token = authService.login(request);

        ResponseCookie accessCookie = cookieUtils.createAccessTokenCookie(token.getAccessToken());
        ResponseCookie refreshCookie = cookieUtils.createRefreshTokenCookie(token.getRefreshToken());

        LoginResponse response = LoginResponse.of(token.getExpiresIn(), token.getUser());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.ok(response));
    }

    /**
     * 토큰 갱신 (Cookie 사용)
     * 새 토큰들은 HttpOnly 쿠키로 전달
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @CookieValue(value = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new IllegalArgumentException("Refresh Token이 쿠키에 없습니다.");
        }

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);

        TokenResponse token = authService.refreshToken(request);

        ResponseCookie accessCookie = cookieUtils.createAccessTokenCookie(token.getAccessToken());
        ResponseCookie refreshCookie = cookieUtils.createRefreshTokenCookie(token.getRefreshToken());

        RefreshResponse response = RefreshResponse.of(token.getExpiresIn());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.ok(response));
    }

    /**
     * 로그아웃 (현재 디바이스)
     * Access Token, Refresh Token 쿠키 모두 만료 처리
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = "refresh_token", required = false) String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        ResponseCookie expiredAccess = cookieUtils.createExpiredAccessTokenCookie();
        ResponseCookie expiredRefresh = cookieUtils.createExpiredRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredAccess.toString())
                .header(HttpHeaders.SET_COOKIE, expiredRefresh.toString())
                .body(ApiResponse.ok(null));
    }

    /**
     * 전체 로그아웃 (모든 디바이스에서 로그아웃)
     * 현재 디바이스의 쿠키도 만료 처리
     */
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(@AuthenticationPrincipal UUID userId) {
        authService.logoutAll(userId);

        ResponseCookie expiredAccess = cookieUtils.createExpiredAccessTokenCookie();
        ResponseCookie expiredRefresh = cookieUtils.createExpiredRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredAccess.toString())
                .header(HttpHeaders.SET_COOKIE, expiredRefresh.toString())
                .body(ApiResponse.ok(null));
    }

    /**
     * 현재 사용자 정보 조회 (JWT 인증 필요)
     */
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal UUID userId) {
        UserResponse user = authService.getUser(userId);
        return ApiResponse.ok(user);
    }

    /**
     * 사용자 프로필 업데이트 (JWT 인증 필요)
     */
    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateMe(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) String avatarUrl) {
        UserResponse user = authService.updateUser(userId, nickname, avatarUrl);
        return ApiResponse.ok(user);
    }
}
