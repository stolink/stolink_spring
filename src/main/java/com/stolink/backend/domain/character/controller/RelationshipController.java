package com.stolink.backend.domain.character.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stolink.backend.domain.character.dto.RelationshipResponse;
import com.stolink.backend.domain.character.service.CharacterService;
import com.stolink.backend.global.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects/{projectId}/relationships")
@RequiredArgsConstructor
public class RelationshipController {

    private final CharacterService characterService;

    @GetMapping
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
                                    .build());
                })
                .collect(java.util.stream.Collectors.toList());

        return ApiResponse.ok(relationships);
    }
}
