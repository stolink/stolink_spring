package com.stolink.backend.domain.character.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.relationship.CharacterRelationship;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Getter
@Builder
public class CharacterResponse {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String id;
    private String projectId;
    private String characterId;
    private String name;
    private String role;
    private String status;
    private Integer age;
    private String gender;
    private String race;
    private String mbti;
    private String backstory;
    private String faction;
    private String imageUrl;

    // Refactored fields: returning Objects instead of JSON strings
    private Object aliases; // List<String>
    private Object profile; // Map
    private Object appearance; // Map
    private Object personality; // Map
    private Object relations; // Map or List
    private Object currentMood; // Map
    private Object meta; // Map
    private Object embedding; // List or parsed object
    private Object inventory; // List

    // Legacy fields - also parsed for consistency, or keeping as Object so they
    // serialize correctly if they were strings.
    // User requested "aliasesJson" -> "aliases" etc.
    // Ideally we rename them to match the Clean REST API style.
    private Object visual;
    private String motivation;
    private String firstAppearance;
    private Object extras;

    private List<CharacterRelationshipResponse> relationships;

    public static CharacterResponse from(Character character) {
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

                // Parse JSON strings to Objects
                .aliases(safeJsonParse(character.getAliasesJson(), List.class, Collections.emptyList()))
                .profile(safeJsonParse(character.getProfileJson(), Map.class, null))
                .appearance(safeJsonParse(character.getAppearanceJson(), Map.class, null))
                .personality(safeJsonParse(character.getPersonalityJson(), Map.class, null))
                .relations(safeJsonParse(character.getRelationsJson(), Object.class, null)) // Could be list or map
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
                        .map(rel -> CharacterRelationshipResponse.from(rel, character.getId()))
                        .collect(Collectors.toList())
                        : Collections.emptyList())
                .build();
    }

    private static <T> T safeJsonParse(String json, Class<T> clazz, T fallback) {
        if (json == null || json.isEmpty()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON for CharacterResponse: {}", json, e);
            return fallback;
        }
    }

    @Getter
    @Builder
    public static class CharacterRelationshipResponse {
        private String id;
        private String sourceId;
        private String targetId;
        private String type;
        private Integer strength;
        private String description;

        public static CharacterRelationshipResponse from(CharacterRelationship relationship, String sourceId) {
            return CharacterRelationshipResponse.builder()
                    .id(relationship.getId() != null ? relationship.getId().toString() : null)
                    .sourceId(sourceId)
                    .targetId(relationship.getTarget() != null ? relationship.getTarget().getId() : null)
                    .type(mapType(relationship.getType()))
                    .strength(relationship.getStrength())
                    .description(relationship.getDescription())
                    .build();
        }

        private static String mapType(String type) {
            if (type == null)
                return "neutral";
            return switch (type.toUpperCase()) {
                case "ALLY" -> "friendly";
                case "ENEMY" -> "hostile";
                case "NO_RELATION" -> "neutral"; // Just in case
                case "ROMANTIC" -> "romantic";
                case "FAMILY" -> "family";
                default -> "neutral";
            };
        }
    }
}
