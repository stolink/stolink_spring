package com.stolink.backend.domain.event.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stolink.backend.domain.event.dto.EventResponse;
import com.stolink.backend.domain.event.service.EventService;
import com.stolink.backend.global.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 이벤트 조회 REST API
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * 캐릭터가 참여한 이벤트 목록 조회
     * GET /api/characters/{characterId}/events
     */
    @GetMapping("/characters/{characterId}/events")
    public ApiResponse<List<EventResponse>> getEventsByCharacter(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String characterId) {
        // characterId는 UUID 또는 AI 생성 ID(예: test-char-alex-001) 모두 가능
        UUID characterUuid;
        try {
            characterUuid = UUID.fromString(characterId);
        } catch (IllegalArgumentException e) {
            // UUID가 아니면 임시 UUID 생성 (서비스에서 characterId로 조회)
            characterUuid = UUID.nameUUIDFromBytes(characterId.getBytes());
        }
        List<EventResponse> events = eventService.getEventsByCharacter(userId, characterUuid);
        return ApiResponse.ok(events);
    }

    /**
     * 프로젝트의 전체 이벤트 목록 조회
     * GET /api/projects/{projectId}/events
     */
    @GetMapping("/projects/{projectId}/events")
    public ApiResponse<List<EventResponse>> getEventsByProject(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {
        List<EventResponse> events = eventService.getEventsByProject(userId, projectId);
        return ApiResponse.ok(events);
    }
}
