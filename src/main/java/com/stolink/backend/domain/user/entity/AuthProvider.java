package com.stolink.backend.domain.user.entity;

/**
 * 인증 제공자 유형
 */
public enum AuthProvider {
    LOCAL, // 일반 이메일/비밀번호 회원가입
    GOOGLE // Google OAuth2
}
