package com.stolink.backend.global.security;

import com.stolink.backend.global.security.jwt.JwtAuthenticationFilter;
import com.stolink.backend.global.security.oauth2.CustomOAuth2UserService;
import com.stolink.backend.global.security.oauth2.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 설정
 *
 * 보안 사항:
 * - 쿠키 기반 JWT 인증
 * - CSRF Origin 검증 (SameSite=Strict + Origin 헤더 검증)
 * - 세션 정책: STATELESS
 * - BCrypt 비밀번호 인코더
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final CsrfOriginFilter csrfOriginFilter;
        private final CustomOAuth2UserService customOAuth2UserService;
        private final OAuth2SuccessHandler oAuth2SuccessHandler;
        private final org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

        @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:5174}")
        private String allowedOrigins;

        @Value("${oauth2.redirect-uri:http://localhost:5173/oauth2/callback}")
        private String oauth2RedirectUri;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                return http
                                // CSRF 비활성화 (JWT 사용하므로 불필요)
                                .csrf(AbstractHttpConfigurer::disable)

                                // CORS 설정
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                                // 세션 관리 - Stateless
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // 요청 권한 설정
                                .authorizeHttpRequests(auth -> auth
                                                // 공개 엔드포인트
                                                .requestMatchers(
                                                                "/api/auth/register",
                                                                "/api/auth/login",
                                                                "/api/auth/refresh",
                                                                "/api/auth/logout",
                                                                "/oauth2/**",
                                                                "/login/oauth2/**",
                                                                "/actuator/health",
                                                                "/actuator/info",
                                                                "/api/internal/**",
                                                                "/error")
                                                .permitAll()
                                                // 그 외 모든 요청은 인증 필요
                                                .anyRequest().authenticated())

                                // OAuth2 로그인 설정
                                .oauth2Login(oauth2 -> oauth2
                                                // 로그인 시작 URL: /api/oauth2/authorization/{registrationId}
                                                .authorizationEndpoint(authorization -> authorization
                                                                .baseUri("/api/oauth2/authorization"))

                                                // 로그인 콜백 URL: /api/login/oauth2/code/{registrationId}
                                                .redirectionEndpoint(redirection -> redirection
                                                                .baseUri("/api/login/oauth2/code/*"))

                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(customOAuth2UserService))
                                                .successHandler(oAuth2SuccessHandler)
                                                // OAuth2 실패 시 프론트엔드로 리다이렉트
                                                .failureHandler((request, response, exception) -> {
                                                        log.error("OAuth2 login failed", exception);
                                                        String errorMessage = exception.getMessage();
                                                        if (errorMessage == null) {
                                                                errorMessage = "oauth_failed";
                                                        }
                                                        String encodedMessage = java.net.URLEncoder.encode(errorMessage,
                                                                        java.nio.charset.StandardCharsets.UTF_8);
                                                        response.sendRedirect(
                                                                        oauth2RedirectUri + "?error=" + encodedMessage);
                                                }))

                                // 인증 되지 않은 경우 401 반환 (기본값인 302 redirect 방지)
                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint(
                                                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                                // 필터 순서: CSRF Origin Filter -> JWT Filter
                                .addFilterBefore(csrfOriginFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(jwtAuthenticationFilter, CsrfOriginFilter.class)

                                .build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();

                // 환경 변수에서 allowed origins 읽기 (쉼표로 구분된 값)
                String[] origins = allowedOrigins.split(",");
                configuration.setAllowedOrigins(Arrays.stream(origins)
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .toList());

                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("*"));
                configuration.setAllowCredentials(true);
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
