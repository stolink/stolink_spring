package com.stolink.backend.global.security;

import com.stolink.backend.global.security.jwt.JwtAuthenticationFilter;
import com.stolink.backend.global.security.oauth2.CustomOAuth2UserService;
import com.stolink.backend.global.security.oauth2.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
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
                                                .authorizationEndpoint(endpoint -> endpoint
                                                                .authorizationRequestResolver(
                                                                                customAuthorizationRequestResolver(
                                                                                                clientRegistrationRepository)))
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(customOAuth2UserService))
                                                .successHandler(oAuth2SuccessHandler)
                                                // OAuth2 실패 시 프론트엔드로 리다이렉트 (prompt=none 실패 등)
                                                .failureHandler((request, response, exception) -> {
                                                        String errorMessage = exception.getMessage();
                                                        String errorCode = "oauth_failed";
                                                        if (errorMessage != null && errorMessage.contains("login_required")) {
                                                                errorCode = "login_required";
                                                        }
                                                        response.sendRedirect(oauth2RedirectUri + "?error=" + errorCode);
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

        private org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver customAuthorizationRequestResolver(
                        org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository) {

                org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver resolver = new org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver(
                                clientRegistrationRepository, "/oauth2/authorization");

                // prompt=none: 계정 선택 화면 없이 자동 로그인
                // 주의: 구글에 로그인 안 되어 있으면 에러 발생 → 프론트에서 처리 필요
                resolver.setAuthorizationRequestCustomizer(builder -> builder
                                .additionalParameters(params -> params.put("prompt", "none")));

                return resolver;
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
