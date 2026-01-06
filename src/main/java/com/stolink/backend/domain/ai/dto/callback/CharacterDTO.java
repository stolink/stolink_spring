package com.stolink.backend.domain.ai.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Character DTO - AI 분석 결과의 캐릭터 데이터
 * callback_result.json의 characters 배열 요소에 대응
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterDTO {

    @JsonProperty("_id")
    private String id;

    private String role;
    private String status;
    private ProfileDTO profile;
    private List<String> aliases;
    private AppearanceDTO appearance;
    private RelationsDTO relations;

    @JsonProperty("current_mood")
    private CurrentMoodDTO currentMood;

    private MetaDTO meta;
    private List<Double> embedding;

    /**
     * Profile nested DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProfileDTO {
        @JsonProperty("character_id")
        private String characterId;

        private String name;
        private Integer age;
        private String gender;
        private String race;
        private String mbti;
        private PersonalityDTO personality;
        private String backstory;
        private FactionDTO faction;
    }

    /**
     * Personality nested DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PersonalityDTO {
        @JsonProperty("core_traits")
        private List<String> coreTraits;

        private List<String> flaws;
        private List<String> values;
    }

    /**
     * Faction nested DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FactionDTO {
        private String name;
        private SocialDTO social;
    }

    /**
     * Social nested DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SocialDTO {
        private String rank;
        private Integer influence;

        @JsonProperty("faction_reputation")
        private Map<String, Object> factionReputation;
    }

    /**
     * Appearance nested DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AppearanceDTO {
        private String physique;

        @JsonProperty("skin_tone")
        private String skinTone;

        private String eyes;
        private String nose;
        private String mouth;

        @JsonProperty("hair_style")
        private String hairStyle;

        @JsonProperty("hair_color")
        private String hairColor;

        private List<String> attire;
        private String expression;

        @JsonProperty("scars_tattoos")
        private List<String> scarsTattoos;

        @JsonProperty("style_context")
        private StyleContextDTO styleContext;
    }

    /**
     * StyleContext nested DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StyleContextDTO {
        @JsonProperty("art_style")
        private String artStyle;
    }

    /**
     * Relations nested DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RelationsDTO {
        private List<GraphRelationDTO> graph;

        @JsonProperty("event_refs")
        private List<String> eventRefs;

        @JsonProperty("location_context")
        private String locationContext;
    }

    /**
     * GraphRelation nested DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GraphRelationDTO {
        private String target;
        private String type;
        private Integer strength;
        private String description;

        @JsonProperty("public_stance")
        private String publicStance;

        @JsonProperty("private_feeling")
        private String privateFeeling;
    }

    /**
     * CurrentMood nested DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CurrentMoodDTO {
        private String emotion;
        private Integer intensity;
        private String trigger;
    }

    /**
     * Meta nested DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetaDTO {
        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("updated_at")
        private String updatedAt;

        @JsonProperty("data_version")
        private String dataVersion;

        @JsonProperty("lock_version")
        private Integer lockVersion;
    }
}
