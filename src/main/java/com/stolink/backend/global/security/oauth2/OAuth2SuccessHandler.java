package com.stolink.backend.global.security.oauth2;

import com.stolink.backend.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * OAuth2 인증 성공 후 JWT 토큰을 발급하고
 * 프론트엔드로 리다이렉트합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

        private final JwtTokenProvider jwtTokenProvider;
        private final com.stolink.backend.domain.user.service.AuthService authService;

        @Value("${oauth2.redirect-uri:http://localhost:3000/oauth2/callback}")
        private String redirectUri;

        @Value("${jwt.cookie-domain}")
        private String cookieDomain;

        @Value("${jwt.cookie-secure}")
        private boolean cookieSecure;

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

                log.info("OAuth2 login success. Issuing tokens for user: {}", userId);

                // Refresh Token을 RDB에 저장
                authService.saveRefreshToken(userId, refreshToken);
                log.debug("Refresh token saved for user: {}", userId);

                ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from("refresh_token", refreshToken)
                                .httpOnly(true)
                                .secure(cookieSecure)
                                .path("/")
                                .maxAge(7 * 24 * 60 * 60)
                                .sameSite("Lax");

                // localhost 환경에서는 domain 설정을 생략하는 것이 호환성에 좋음
                if (cookieDomain != null && !cookieDomain.isEmpty() && !cookieDomain.contains("localhost")) {
                        cookieBuilder.domain(cookieDomain);
                }

                ResponseCookie refreshCookie = cookieBuilder.build();
                log.debug("Refresh-Cookie created: {}", refreshCookie.toString());

                // Access Token을 쿠키로 설정
                ResponseCookie.ResponseCookieBuilder accessCookieBuilder = ResponseCookie.from("access_token", accessToken)
                                .httpOnly(true)
                                .secure(cookieSecure)
                                .path("/")
                                .maxAge(jwtTokenProvider.getAccessTokenExpirySeconds())
                                .sameSite("Lax");

                if (cookieDomain != null && !cookieDomain.isEmpty() && !cookieDomain.contains("localhost")) {
                        accessCookieBuilder.domain(cookieDomain);
                }
                ResponseCookie accessCookie = accessCookieBuilder.build();
                log.debug("Access-Cookie created: {}", accessCookie.toString());

                response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, refreshCookie.toString());
                response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, accessCookie.toString());

                // 프론트엔드로 리다이렉트 (success=true만 전달)
                // refresh_token은 쿠키로 설정됨
                // 프론트엔드는 /api/auth/refresh 호출하여 accessToken 발급받아야 함
                String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                                .queryParam("success", "true")
                                .build().toUriString();

                getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
}
