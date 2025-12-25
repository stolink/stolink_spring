package com.stolink.backend.domain.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Analysis Worker로부터 받는 분석 결과 콜백 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisCallbackDTO {

    private String jobId;
    private UUID projectId;
    private UUID documentId;
    private String status; // "SUCCESS" or "FAILED"
    private String errorMessage;

    private List<CharacterInfo> characters;
    private List<RelationshipInfo> relationships;
    private List<ForeshadowingInfo> foreshadowings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterInfo {
        private String name;
        private String description;
        private String role;
        private Map<String, Object> attributes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationshipInfo {
        private String sourceCharacter;
        private String targetCharacter;
        private String relationshipType;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForeshadowingInfo {
        private String title;
        private String hint;
        private String revelation;
        private Integer chapterHint;
        private Integer chapterRevelation;
    }
}
