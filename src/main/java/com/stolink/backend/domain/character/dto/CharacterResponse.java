package com.stolink.backend.domain.character.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.character.node.Character;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Builder
public class CharacterResponse {
    @JsonProperty("_id")
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
        @JsonProperty("target")
        private String targetId;
        private List<String> types;
        private Integer strength;
        private String description;

        public String getType() {
            return (types != null && !types.isEmpty()) ? types.get(0) : "NEUTRAL";
        }
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public static CharacterResponse from(Character character) {
        List<CharacterRelationshipResponse> baseRelationships = mapRelationships(character);
        Object relationsObj = safeJsonParseMap(character.getRelationsJson());

        // Enrichment for Option A: Ensure relations.graph includes Neo4j relationships
        if (baseRelationships != null && !baseRelationships.isEmpty()) {
            Map<String, Object> relationsMap;
            if (relationsObj instanceof Map) {
                relationsMap = new java.util.HashMap<>((Map<String, Object>) relationsObj);
            } else {
                relationsMap = new java.util.HashMap<>();
            }

            List<Map<String, Object>> graph = (List<Map<String, Object>>) relationsMap.get("graph");
            if (graph == null) {
                graph = new java.util.ArrayList<>();
            } else {
                graph = new java.util.ArrayList<>(graph);
            }

            for (CharacterRelationshipResponse rel : baseRelationships) {
                boolean exists = graph.stream()
                        .anyMatch(g -> String.valueOf(g.get("target")).equals(rel.getTargetId()));
                if (!exists) {
                    Map<String, Object> gRel = new java.util.HashMap<>();
                    gRel.put("target", rel.getTargetId());
                    gRel.put("type", rel.getType());
                    gRel.put("strength", rel.getStrength());
                    gRel.put("description", rel.getDescription());
                    graph.add(gRel);
                }
            }
            relationsMap.put("graph", graph);
            relationsObj = relationsMap;
        }

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
                .relations(relationsObj)
                .currentMood(safeJsonParseMap(character.getCurrentMoodJson()))
                .meta(safeJsonParseMap(character.getMetaJson()))
                .embedding(safeJsonParseList(character.getEmbeddingJson()))
                .inventory(safeJsonParseMap(character.getInventoryJson()))
                .visual(safeJsonParseMap(character.getVisualJson()))
                .motivation(character.getMotivation())
                .firstAppearance(character.getFirstAppearance())
                .extras(safeJsonParseMap(character.getExtrasJson()))
                .relationships(baseRelationships)
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
                        .types(mapRelationshipTypes(rel.getTypes()))
                        .strength(rel.getStrength())
                        .description(rel.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    private static List<String> mapRelationshipTypes(List<String> types) {
        if (types == null || types.isEmpty())
            return Collections.emptyList();

        return types.stream()
                .map(type -> {
                    if (type == null)
                        return null;
                    String upper = type.toUpperCase();
                    return switch (upper) {
                        case "FRIENDLY", "ALLY" -> "ALLY";
                        case "HOSTILE", "ENEMY" -> "ENEMY";
                        case "FAMILY" -> "FAMILY";
                        case "ROMANTIC" -> "ROMANTIC";
                        case "NEUTRAL" -> "NEUTRAL";
                        default -> upper;
                    };
                })
                .collect(Collectors.toList());
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
