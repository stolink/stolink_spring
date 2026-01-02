package com.stolink.backend.domain.project.controller;

import com.stolink.backend.global.sse.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 프로젝트 상태 SSE 컨트롤러
 *
 * 분석 진행 상황을 실시간으로 클라이언트에게 전송합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/project")
@RequiredArgsConstructor
public class ProjectStatusController {

    private final SseEmitterService sseEmitterService;

    /**
     * 프로젝트 분석 상태 스트림
     *
     * 클라이언트는 이 엔드포인트에 연결하여 실시간 상태 업데이트를 받습니다.
     */
    @GetMapping(value = "/{projectId}/status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStatus(@PathVariable UUID projectId) {
        log.info("SSE stream requested for project: {}", projectId);
        return sseEmitterService.createEmitter(projectId);
    }
}
