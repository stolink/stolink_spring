package com.stolink.backend.domain.user.dto;

import com.stolink.backend.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JWT 토큰 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn; // seconds
    private String tokenType;
    private UserResponse user;

    public static TokenResponse of(String accessToken, String refreshToken, Long expiresInSeconds, User user) {
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresInSeconds)
                .tokenType("Bearer")
                .user(UserResponse.from(user))
                .build();
    }
}
