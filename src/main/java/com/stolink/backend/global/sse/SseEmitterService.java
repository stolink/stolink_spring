package com.stolink.backend.global.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE (Server-Sent Events) 연결 관리 서비스
 *
 * 프로젝트별 실시간 상태 알림을 관리합니다.
 */
@Slf4j
@Service
public class SseEmitterService {

    // projectId -> SseEmitter (1:N 관계도 가능하지만 간단히 1:1로 구현)
    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    private static final long TIMEOUT = 60 * 60 * 1000L; // 1시간

    /**
     * 프로젝트에 대한 SSE 연결을 생성합니다.
     */
    public SseEmitter createEmitter(UUID projectId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);

        emitter.onCompletion(() -> {
            log.debug("SSE completed for project: {}", projectId);
            emitters.remove(projectId);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE timeout for project: {}", projectId);
            emitters.remove(projectId);
        });

        emitter.onError(e -> {
            log.error("SSE error for project: {}", projectId, e);
            emitters.remove(projectId);
        });

        emitters.put(projectId, emitter);
        log.info("SSE emitter created for project: {}", projectId);

        return emitter;
    }

    /**
     * 프로젝트에 상태 업데이트를 전송합니다.
     */
    public void sendStatus(UUID projectId, AnalysisStatusEvent event) {
        SseEmitter emitter = emitters.get(projectId);
        if (emitter == null) {
            log.debug("No SSE emitter for project: {}", projectId);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(event));
            log.debug("SSE status sent for project: {}", projectId);
        } catch (IOException e) {
            log.error("Failed to send SSE for project: {}", projectId, e);
            emitters.remove(projectId);
        }
    }

    /**
     * 프로젝트의 SSE 연결을 종료합니다.
     */
    public void complete(UUID projectId) {
        SseEmitter emitter = emitters.remove(projectId);
        if (emitter != null) {
            emitter.complete();
            log.info("SSE completed for project: {}", projectId);
        }
    }

    /**
     * 30초마다 모든 활성 SSE 연결에 heartbeat를 전송합니다.
     * 프록시/로드밸런서가 유휴 연결을 끊는 것을 방지합니다.
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }

        log.debug("Sending heartbeat to {} SSE connections", emitters.size());

        emitters.forEach((projectId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data("ping"));
            } catch (IOException e) {
                log.debug("Heartbeat failed for project: {}, removing emitter", projectId);
                emitters.remove(projectId);
            }
        });
    }

    /**
     * 분석 상태 이벤트 DTO
     */
    public record AnalysisStatusEvent(
            String status,
            int completedDocuments,
            int totalDocuments,
            String message) {
    }
}
