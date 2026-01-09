package com.stolink.backend.domain.character.controller;

import com.stolink.backend.domain.character.dto.RelationshipResponse;
import com.stolink.backend.domain.character.service.CharacterService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/relationships")
@RequiredArgsConstructor
public class RelationshipController {

    private final CharacterService characterService;

    @GetMapping
    public ApiResponse<List<RelationshipResponse>> getRelationships(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {
        List<RelationshipResponse> relationships = characterService.getRelationshipsByProjectId(userId, projectId);
        return ApiResponse.ok(relationships);
    }
}
