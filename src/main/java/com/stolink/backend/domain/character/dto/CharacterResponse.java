package com.stolink.backend.domain.character.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.character.node.Character;

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

    private Object aliases;
    private Object profile;
    private Object appearance;
    private Object personality;
    private Object relations;
    private Object currentMood;
    private Object meta;
    private Object embedding;
    private Object inventory;

    private Object visual;
    private String motivation;
    private String firstAppearance;
    private Object extras;

    private List<CharacterRelationshipResponse> relationships;

    @Getter
    @Builder
    public static class CharacterRelationshipResponse {
        private String id;
        private String sourceId;
        private String targetId;
        private String type;
        private Integer strength;
        private String description;
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static CharacterResponse from(Character character) {
        return CharacterResponse.builder()
                .id(character.getId())
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
                .aliases(safeJsonParseList(character.getAliasesJson()))
                .profile(safeJsonParseMap(character.getProfileJson()))
                .appearance(safeJsonParseMap(character.getAppearanceJson()))
                .personality(safeJsonParseMap(character.getPersonalityJson()))
                .relations(safeJsonParseMap(character.getRelationsJson()))
                .currentMood(safeJsonParseMap(character.getCurrentMoodJson()))
                .meta(safeJsonParseMap(character.getMetaJson()))
                .embedding(safeJsonParseList(character.getEmbeddingJson()))
                .inventory(safeJsonParseMap(character.getInventoryJson()))
                .visual(safeJsonParseMap(character.getVisualJson()))
                .motivation(character.getMotivation())
                .firstAppearance(character.getFirstAppearance())
                .extras(safeJsonParseMap(character.getExtrasJson()))
                .relationships(mapRelationships(character))
                .build();
    }

    private static List<CharacterRelationshipResponse> mapRelationships(Character character) {
        if (character.getRelationships() == null) {
            return Collections.emptyList();
        }
        return character.getRelationships().stream()
                .map(rel -> CharacterRelationshipResponse.builder()
                        .id(rel.getId() != null ? String.valueOf(rel.getId()) : null)
                        .sourceId(character.getId())
                        .targetId(rel.getTarget() != null ? rel.getTarget().getId() : null)
                        .type(mapRelationshipType(rel.getType()))
                        .strength(rel.getStrength())
                        .description(rel.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    private static String mapRelationshipType(String type) {
        if (type == null)
            return null;
        return switch (type.toUpperCase()) {
            case "ALLY" -> "friendly";
            case "ENEMY" -> "hostile";
            case "FAMILY" -> "family";
            case "ROMANTIC" -> "romantic";
            case "NEUTRAL" -> "neutral";
            default -> type.toLowerCase();
        };
    }

    private static Object safeJsonParseList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static Object safeJsonParseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON map: {}", e.getMessage());
            return null;
        }
    }
}
