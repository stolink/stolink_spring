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
}
