package com.stolink.backend.domain.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

/**
 * 이미지 생성 서버 헬스체크 서비스
 * 작업을 큐에 보내기 전에 이미지 서버가 정상인지 확인
 */
@Slf4j
@Service
public class ImageServerHealthChecker {

    private final RestTemplate restTemplate;

    @Value("${app.image-server.url:http://localhost:8000}")
    private String imageServerUrl;

    @Value("${app.image-server.health-endpoint:/health}")
    private String healthEndpoint;

    @Value("${app.image-server.health-check-enabled:true}")
    private boolean healthCheckEnabled;

    @Value("${app.image-server.health-timeout-ms:5000}")
    private int healthTimeoutMs;

    public ImageServerHealthChecker() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 이미지 서버 헬스체크 수행
     * 
     * @return true if healthy, false otherwise
     */
    public boolean isHealthy() {
        if (!healthCheckEnabled) {
            log.debug("Image server health check is disabled");
            return true;
        }

        String healthUrl = imageServerUrl + healthEndpoint;

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.debug("Image server is healthy: {}", healthUrl);
                return true;
            } else {
                log.warn("Image server returned non-OK status: {} from {}",
                        response.getStatusCode(), healthUrl);
                return false;
            }
        } catch (Exception e) {
            log.error("Image server health check failed: {} - {}", healthUrl, e.getMessage());
            return false;
        }
    }

    /**
     * 이미지 서버가 정상이 아니면 예외 발생
     * 
     * @throws ImageServerNotHealthyException if server is not healthy
     */
    public void checkHealthOrThrow() {
        if (!isHealthy()) {
            throw new ImageServerNotHealthyException(
                    "Image server is not available at " + imageServerUrl + healthEndpoint);
        }
    }

    /**
     * 이미지 서버 URL 반환 (로깅/디버깅용)
     */
    public String getImageServerUrl() {
        return imageServerUrl;
    }

    /**
     * 이미지 서버가 정상이 아닐 때 발생하는 예외
     */
    public static class ImageServerNotHealthyException extends RuntimeException {
        public ImageServerNotHealthyException(String message) {
            super(message);
        }
    }
}
