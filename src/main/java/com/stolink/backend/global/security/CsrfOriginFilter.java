package com.stolink.backend.global.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CSRF Origin 검증 필터
 *
 * 쿠키 기반 인증 사용 시 CSRF 공격 방지를 위해
 * 상태 변경 요청(POST, PUT, PATCH, DELETE)에 대해 Origin 헤더를 검증합니다.
 *
 * SameSite=Strict 쿠키와 함께 사용하여 이중 보호를 제공합니다.
 */
@Slf4j
@Component
public class CsrfOriginFilter extends OncePerRequestFilter {

    @Value("${app.cors.allowed-origins}")
    private String allowedOriginsConfig;

    private Set<String> allowedOrigins;

    @PostConstruct
    public void init() {
        allowedOrigins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        log.info("CSRF Origin Filter initialized with allowed origins: {}", allowedOrigins);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String method = request.getMethod();

        // GET, HEAD, OPTIONS, TRACE는 안전한 메서드로 간주
        if (isSafeMethod(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 상태 변경 요청에 대해 Origin 검증
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");

        if (!isValidOrigin(origin, referer)) {
            log.warn("CSRF Origin validation failed. Origin: {}, Referer: {}, URI: {}, Method: {}",
                    origin, referer, request.getRequestURI(), method);

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"error\":\"CSRF validation failed\",\"message\":\"Invalid origin\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method) ||
               "HEAD".equalsIgnoreCase(method) ||
               "OPTIONS".equalsIgnoreCase(method) ||
               "TRACE".equalsIgnoreCase(method);
    }

    private boolean isValidOrigin(String origin, String referer) {
        // Origin 헤더가 있으면 우선 검증
        if (StringUtils.hasText(origin)) {
            return allowedOrigins.contains(origin);
        }

        // Origin이 없으면 Referer 검증 (일부 브라우저/상황에서 Origin 미전송)
        if (StringUtils.hasText(referer)) {
            try {
                URI refererUri = new URI(referer);
                String refererOrigin = refererUri.getScheme() + "://" + refererUri.getHost();
                if (refererUri.getPort() != -1 && refererUri.getPort() != 80 && refererUri.getPort() != 443) {
                    refererOrigin += ":" + refererUri.getPort();
                }
                return allowedOrigins.contains(refererOrigin);
            } catch (Exception e) {
                log.warn("Invalid Referer header: {}", referer);
                return false;
            }
        }

        // 둘 다 없으면 요청 거부
        // 참고: same-origin 요청에서도 Origin이 없을 수 있음 (서버 to 서버 통신 등)
        // 이 경우는 shouldNotFilter에서 처리
        return false;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // 내부 API는 CSRF 검증 스킵 (서비스 간 통신)
        if (path.startsWith("/api/internal/")) {
            return true;
        }

        // Actuator 엔드포인트 스킵
        if (path.startsWith("/actuator/")) {
            return true;
        }

        // OAuth2 콜백 스킵 (외부 서비스에서 리다이렉트)
        if (path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/")) {
            return true;
        }

        return false;
    }
}
