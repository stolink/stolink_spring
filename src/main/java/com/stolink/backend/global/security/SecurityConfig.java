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
 * - CSRF 비활성화 (Stateless JWT 사용)
 * - 세션 정책: STATELESS
 * - BCrypt 비밀번호 인코더
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final CustomOAuth2UserService customOAuth2UserService;
        private final OAuth2SuccessHandler oAuth2SuccessHandler;

        @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:5174}")
        private String allowedOrigins;

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
                                                                "/error")
                                                .permitAll()
                                                // 그 외 모든 요청은 인증 필요
                                                .anyRequest().authenticated())

                                // OAuth2 로그인 설정
                                .oauth2Login(oauth2 -> oauth2
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(customOAuth2UserService))
                                                .successHandler(oAuth2SuccessHandler))

                                // 인증 되지 않은 경우 401 반환 (기본값인 302 redirect 방지)
                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint(
                                                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                                // JWT 필터 추가
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

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
