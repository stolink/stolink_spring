package com.stolink.backend.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 쿠키 기반 로그인 응답 DTO
 * 토큰은 HttpOnly 쿠키로 전달되므로 응답 body에 포함하지 않음
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private Long expiresIn; // Access Token 만료 시간 (seconds)
    private UserResponse user;

    public static LoginResponse of(Long expiresInSeconds, UserResponse user) {
        return LoginResponse.builder()
                .expiresIn(expiresInSeconds)
                .user(user)
                .build();
    }
}
