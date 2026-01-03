package com.stolink.backend.global.security.oauth2;

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
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

/**
 * OAuth2 로그인 성공 핸들러
 *
 * OAuth2 인증 성공 후 JWT 토큰을 HttpOnly 쿠키로 발급하고
 * 프론트엔드로 리다이렉트합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

        private final JwtTokenProvider jwtTokenProvider;
        private final com.stolink.backend.domain.user.service.AuthService authService;
        private final CookieUtils cookieUtils;

        @Value("${oauth2.redirect-uri:http://localhost:3000/oauth2/callback}")
        private String redirectUri;

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request,
                        HttpServletResponse response,
                        Authentication authentication) throws IOException {
                OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

                // CustomOAuth2UserService에서 설정한 userId 추출
                String userIdStr = (String) oAuth2User.getAttributes().get("userId");
                UUID userId = UUID.fromString(userIdStr);

                // JWT 토큰 생성
                String accessToken = jwtTokenProvider.createAccessToken(userId);
                String refreshToken = jwtTokenProvider.createRefreshToken(userId);

                log.info("OAuth2 login success. Issuing JWT for user: {}", userId);

                // Refresh Token을 RDB에 저장
                authService.saveRefreshToken(userId, refreshToken);

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
}
