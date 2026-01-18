package com.stolink.backend.domain.character.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stolink.backend.domain.character.dto.RelationshipCreateRequest;
import com.stolink.backend.domain.character.dto.RelationshipResponse;
import com.stolink.backend.domain.character.dto.RelationshipUpdateRequest;
import com.stolink.backend.domain.character.service.CharacterService;
import com.stolink.backend.global.common.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RelationshipController {

    private final CharacterService characterService;

    /**
     * 프로젝트의 모든 관계 조회
     */
    @GetMapping("/api/projects/{projectId}/relationships")
    public ApiResponse<List<RelationshipResponse>> getRelationships(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {

        // Use the robust method we fixed in CharacterService
        List<com.stolink.backend.domain.character.node.Character> characters = characterService
                .getCharactersWithRelationships(userId, projectId);

        // Flatten relationships from all characters
        List<RelationshipResponse> relationships = characters.stream()
                .flatMap(c -> {
                    if (c.getRelationships() == null)
                        return java.util.stream.Stream.empty();
                    return c.getRelationships().stream()
                            .map(r -> RelationshipResponse.builder()
                                    .id(String.valueOf(r.getId()))
                                    .sourceId(c.getId())
                                    // Handle potential null target safely (though our fix ensures it shouldn't be
                                    // null)
                                    .targetId(r.getTarget() != null ? r.getTarget().getId() : null)
                                    .types(r.getTypes())
                                    .strength(r.getStrength())
                                    .description(r.getDescription())
                                    .bidirectional(r.getBidirectional())
                                    .since(r.getSince())
                                    .build());
                })
                .collect(java.util.stream.Collectors.toList());

        return ApiResponse.ok(relationships);
    }

    /**
     * 관계 생성
     * POST /api/projects/{projectId}/relationships
     *
     * @apiNote bidirectional: true면 역방향 관계도 자동 생성
     */
    @PostMapping("/api/projects/{projectId}/relationships")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RelationshipResponse> createRelationship(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId,
            @Valid @RequestBody RelationshipCreateRequest request) {

        RelationshipResponse response = characterService.createRelationshipWithResponse(userId, projectId, request);
        return ApiResponse.created(response);
    }

    /**
     * 단일 관계 조회
     * GET /api/projects/{projectId}/relationships/{id}
     */
    @GetMapping("/api/projects/{projectId}/relationships/{id}")
    public ApiResponse<RelationshipResponse> getRelationship(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId,
            @PathVariable String id) {

        RelationshipResponse response = characterService.getRelationshipById(userId, projectId, id);
        return ApiResponse.ok(response);
    }

    /**
     * 관계 수정 (Partial Update)
     * PATCH /api/projects/{projectId}/relationships/{id}
     */
    @PatchMapping("/api/projects/{projectId}/relationships/{id}")
    public ApiResponse<RelationshipResponse> updateRelationship(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId,
            @PathVariable String id,
            @Valid @RequestBody RelationshipUpdateRequest request) {

        RelationshipResponse response = characterService.updateRelationship(userId, projectId, id, request);
        return ApiResponse.ok(response);
    }

    /**
     * 관계 삭제
     * DELETE /api/projects/{projectId}/relationships/{id}
     *
     * @apiNote bidirectional: true인 관계는 역방향도 함께 삭제
     */
    @DeleteMapping("/api/projects/{projectId}/relationships/{id}")
    public ApiResponse<Void> deleteRelationship(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId,
            @PathVariable String id) {

        characterService.deleteRelationship(userId, projectId, id);
        return ApiResponse.ok();
    }

    // =========== Global Relationship Endpoints (No projectId in path) ===========

    /**
     * 관계 생성 (Global - projectId를 sourceId에서 유추)
     * POST /api/relationships
     *
     * @apiNote projectId는 sourceId 캐릭터에서 자동으로 추론됨
     * @apiNote sourceId와 targetId가 동일한 프로젝트에 속하는지 검증
     * @apiNote bidirectional: true면 역방향 관계도 자동 생성
     */
    @PostMapping("/api/relationships")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RelationshipResponse> createRelationshipGlobal(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody RelationshipCreateRequest request) {

        RelationshipResponse response = characterService.createRelationshipWithResponse(userId, request);
        return ApiResponse.created(response);
    }

    /**
     * 관계 수정 (Global)
     * PATCH /api/relationships/{id}
     */
    @PatchMapping("/api/relationships/{id}")
    public ApiResponse<RelationshipResponse> updateRelationshipGlobal(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String id,
            @Valid @RequestBody RelationshipUpdateRequest request) {

        RelationshipResponse response = characterService.updateRelationship(userId, id, request);
        return ApiResponse.ok(response);
    }

    /**
     * 관계 삭제 (Global)
     * DELETE /api/relationships/{id}
     */
    @DeleteMapping("/api/relationships/{id}")
    public ApiResponse<Void> deleteRelationshipGlobal(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String id) {

        characterService.deleteRelationship(userId, id);
        return ApiResponse.ok();
    }
}
