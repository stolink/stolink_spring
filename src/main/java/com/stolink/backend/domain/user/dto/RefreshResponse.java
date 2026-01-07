package com.stolink.backend.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 쿠키 기반 토큰 갱신 응답 DTO
 * 토큰은 HttpOnly 쿠키로 전달되므로 응답 body에 포함하지 않음
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshResponse {
    private Long expiresIn; // Access Token 만료 시간 (seconds)

    public static RefreshResponse of(Long expiresInSeconds) {
        return RefreshResponse.builder()
                .expiresIn(expiresInSeconds)
                .build();
    }
}
