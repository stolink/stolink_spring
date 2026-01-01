package com.stolink.backend.domain.character.dto;

import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.relationship.CharacterRelationship;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

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

    // JSON fields (returning raw JSON string or parsed object depends on frontend
    // needs, keeping as string for now to match entity)
    private String aliasesJson;
    private String profileJson;
    private String appearanceJson;
    private String personalityJson;
    private String relationsJson;
    private String currentMoodJson;
    private String metaJson;
    private String embeddingJson;

    // Legacy
    private String visualJson;
    private String motivation;
    private String firstAppearance;
    private String extrasJson;

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
                .aliasesJson(character.getAliasesJson())
                .profileJson(character.getProfileJson())
                .appearanceJson(character.getAppearanceJson())
                .personalityJson(character.getPersonalityJson())
                .relationsJson(character.getRelationsJson())
                .currentMoodJson(character.getCurrentMoodJson())
                .metaJson(character.getMetaJson())
                .embeddingJson(character.getEmbeddingJson())
                .visualJson(character.getVisualJson())
                .motivation(character.getMotivation())
                .firstAppearance(character.getFirstAppearance())
                .extrasJson(character.getExtrasJson())
                .relationships(character.getRelationships().stream()
                        .map(CharacterRelationshipResponse::from)
                        .collect(Collectors.toList()))
                .build();
    }

    @Getter
    @Builder
    public static class CharacterRelationshipResponse {
        private String id;
        private String targetCharacterName;
        private String type;
        private Integer strength;
        private String description;

        public static CharacterRelationshipResponse from(CharacterRelationship relationship) {
            return CharacterRelationshipResponse.builder()
                    .id(relationship.getId() != null ? relationship.getId().toString() : null)
                    .targetCharacterName(
                            relationship.getTarget() != null ? relationship.getTarget().getName()
                                    : null)
                    .type(relationship.getType())
                    .strength(relationship.getStrength())
                    .description(relationship.getDescription())
                    .build();
        }
    }
}
