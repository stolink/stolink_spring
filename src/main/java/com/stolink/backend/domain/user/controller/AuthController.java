package com.stolink.backend.domain.user.controller;

import com.stolink.backend.domain.user.dto.*;
import com.stolink.backend.domain.user.service.AuthService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.cookie-domain}")
    private String cookieDomain;

    /**
     * 일반 회원가입
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@RequestBody RegisterRequest request) {
        TokenResponse token = authService.register(request);
        ResponseCookie cookie = createRefreshTokenCookie(token.getRefreshToken());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.created(token));
    }

    /**
     * 일반 로그인
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@RequestBody LoginRequest request) {
        TokenResponse token = authService.login(request);
        ResponseCookie cookie = createRefreshTokenCookie(token.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(token));
    }

    /**
     * 토큰 갱신 (Cookie 사용)
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(value = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new IllegalArgumentException("Refresh Token이 쿠키에 없습니다.");
        }

        // DTO로 감싸서 서비스 호출 (서비스 계층 변경 최소화)
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);

        TokenResponse token = authService.refreshToken(request);
        ResponseCookie cookie = createRefreshTokenCookie(token.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(token));
    }

    /**
     * 로그아웃 (현재 디바이스)
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = "refresh_token", required = false) String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .domain(cookieDomain) // SSO Domain
                .maxAge(0) // 즉시 만료
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(null));
    }

    /**
     * 전체 로그아웃 (모든 디바이스에서 로그아웃)
     */
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(@AuthenticationPrincipal UUID userId) {
        authService.logoutAll(userId);
        return ApiResponse.ok(null);
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

    private ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true) // HTTPS or Localhost
                .path("/")
                .domain(cookieDomain) // SSO Domain
                .maxAge(7 * 24 * 60 * 60) // 7 days (match with property)
                .sameSite("Lax")
                .build();
    }
}
