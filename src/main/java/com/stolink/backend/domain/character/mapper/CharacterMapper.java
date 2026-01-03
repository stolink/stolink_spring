package com.stolink.backend.domain.character.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.character.dto.CharacterResponse;
import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.relationship.CharacterRelationship;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterMapper {

    private final ObjectMapper objectMapper;

    public CharacterResponse toResponse(Character character) {
        if (character == null)
            return null;

        return CharacterResponse.builder()
                .id(character.getId())
                .projectId(character.getProjectId())
                .characterId(character.getCharacterId())
                .name(character.getName())
                .role(character.getRole())
                .status(character.getStatus())
                .age(character.getAge())
                .gender(character.getGender())
                .race(character.getRace())
                .mbti(character.getMbti())
                .backstory(character.getBackstory())
                .faction(character.getFaction())
                .imageUrl(character.getImageUrl())

                // Parse JSON strings to Objects using Spring-managed ObjectMapper
                .aliases(safeJsonParse(character.getAliasesJson(), List.class, Collections.emptyList()))
                .profile(safeJsonParse(character.getProfileJson(), Map.class, null))
                .appearance(safeJsonParse(character.getAppearanceJson(), Map.class, null))
                .personality(safeJsonParse(character.getPersonalityJson(), Map.class, null))
                .relations(safeJsonParse(character.getRelationsJson(), Object.class, null))
                .currentMood(safeJsonParse(character.getCurrentMoodJson(), Map.class, null))
                .meta(safeJsonParse(character.getMetaJson(), Map.class, null))
                .embedding(safeJsonParse(character.getEmbeddingJson(), List.class, null))
                .inventory(safeJsonParse(character.getInventoryJson(), List.class, Collections.emptyList()))

                // Legacy
                .visual(safeJsonParse(character.getVisualJson(), Map.class, null))
                .motivation(character.getMotivation())
                .firstAppearance(character.getFirstAppearance())
                .extras(safeJsonParse(character.getExtrasJson(), Map.class, null))

                .relationships(character.getRelationships() != null ? character.getRelationships().stream()
                        .map(rel -> toRelationshipResponse(rel, character.getId()))
                        .collect(Collectors.toList())
                        : Collections.emptyList())
                .build();
    }

    private CharacterResponse.CharacterRelationshipResponse toRelationshipResponse(CharacterRelationship relationship,
            String sourceId) {
        return CharacterResponse.CharacterRelationshipResponse.builder()
                .id(relationship.getId() != null ? relationship.getId().toString() : null)
                .sourceId(sourceId)
                .targetId(relationship.getTarget() != null ? relationship.getTarget().getId() : null)
                .type(mapType(relationship.getType()))
                .strength(relationship.getStrength())
                .description(relationship.getDescription())
                .build();
    }

    private <T> T safeJsonParse(String json, Class<T> clazz, T fallback) {
        if (json == null || json.isEmpty()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON for character mapping: {}", json, e);
            return fallback;
        }
    }

    private String mapType(String type) {
        if (type == null)
            return "neutral";
        return switch (type.toUpperCase()) {
            case "ALLY" -> "friendly";
            case "ENEMY" -> "hostile";
            case "NO_RELATION" -> "neutral";
            case "ROMANTIC" -> "romantic";
            case "FAMILY" -> "family";
            default -> "neutral";
        };
    }
}
