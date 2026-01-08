package com.stolink.backend.global.security.oauth2;

import com.stolink.backend.domain.user.entity.AuthProvider;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.security.jwt.JwtTokenProvider;
import com.stolink.backend.global.util.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * OAuth2 로그인 성공 핸들러
 *
 * OAuth2 인증 성공 후 JWT 토큰을 HttpOnly 쿠키로 발급하고
 * 프론트엔드로 리다이렉트합니다.
 *
 * Note: Google OAuth2는 OIDC를 사용하므로 CustomOAuth2UserService의 userId가
 * attributes에 포함되지 않을 수 있습니다. 이 경우 email로 DB에서 사용자를 조회합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

        private final JwtTokenProvider jwtTokenProvider;
        private final com.stolink.backend.domain.user.service.AuthService authService;
        private final CookieUtils cookieUtils;
        private final UserRepository userRepository;

        @Value("${oauth2.redirect-uri:http://localhost:3000/oauth2/callback}")
        private String redirectUri;

        @Override
        // Transaction removed to avoid holding DB connection during redirect/IO
        public void onAuthenticationSuccess(HttpServletRequest request,
                        HttpServletResponse response,
                        Authentication authentication) throws IOException {
                OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

                // OAuth2 attributes에서 userId 추출 시도
                String userIdStr = (String) oAuth2User.getAttributes().get("userId");
                UUID userId;

                if (userIdStr != null) {
                        // CustomOAuth2UserService에서 설정한 userId 사용
                        userId = UUID.fromString(userIdStr);
                        log.info("OAuth2 login: userId found in attributes: {}", userId);
                } else {
                        // OIDC의 경우 userId가 없으므로 email로 사용자 조회/생성
                        String email = (String) oAuth2User.getAttributes().get("email");
                        String googleId = (String) oAuth2User.getAttributes().get("sub");
                        String name = (String) oAuth2User.getAttributes().get("name");
                        String picture = (String) oAuth2User.getAttributes().get("picture");

                        log.info("OIDC login detected. Looking up user by email: {}", email);

                        User user = findOrCreateGoogleUser(email, googleId, name, picture);
                        userId = user.getId();
                        log.info("OAuth2 login: user resolved from DB. userId={}", userId);
                }

                // JWT 토큰 생성
                String accessToken = jwtTokenProvider.createAccessToken(userId);
                String refreshToken = jwtTokenProvider.createRefreshToken(userId);

                log.info("OAuth2 login success. Issuing tokens for user: {}", userId);

                // Refresh Token을 RDB에 저장
                authService.saveRefreshToken(userId, refreshToken);
                log.debug("Refresh token saved for user: {}", userId);

                // Access Token, Refresh Token을 HttpOnly 쿠키로 설정
                ResponseCookie accessCookie = cookieUtils.createAccessTokenCookie(accessToken);
                ResponseCookie refreshCookie = cookieUtils.createRefreshTokenCookie(refreshToken);

                response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
                response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

                // 프론트엔드로 리다이렉트 (토큰은 쿠키로 전달되므로 URL에 포함하지 않음)
                String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                                .queryParam("success", "true")
                                .build().toUriString();

                getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }

        /**
         * Google 사용자 조회 또는 생성
         */
        @Transactional
        private User findOrCreateGoogleUser(String email, String googleId, String name, String picture) {
                // 1. Google ID로 조회
                Optional<User> existingUser = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, googleId);
                if (existingUser.isPresent()) {
                        return existingUser.get();
                }

                // 2. 이메일로 조회
                Optional<User> userByEmail = userRepository.findByEmail(email);
                if (userByEmail.isPresent()) {
                        User user = userByEmail.get();
                        if (user.getProvider() == AuthProvider.LOCAL) {
                                throw new IllegalStateException("이미 일반 회원가입으로 등록된 이메일입니다. 기존 계정으로 로그인해주세요.");
                        }
                        // Google Provider이지만 ID가 다른 경우 - ID 업데이트
                        user.updateProviderId(googleId);
                        return userRepository.save(user);
                }

                // 3. 신규 사용자 생성
                User newUser = User.builder()
                        .email(email)
                        .nickname(name != null ? name : email.split("@")[0])
                        .avatarUrl(picture)
                        .provider(AuthProvider.GOOGLE)
                        .providerId(googleId)
                        .build();

                return userRepository.save(newUser);
        }
}
