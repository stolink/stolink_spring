package com.stolink.backend.domain.ai.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Analysis Worker로부터 받는 분석 결과 콜백 DTO
 */
@Getter
@AllArgsConstructor
public class AnalysisCallbackDTO {

    private String jobId;
    private UUID projectId;
    private UUID documentId;
    private String status; // "SUCCESS" or "FAILED"
    private String errorMessage;

    private List<CharacterInfo> characters = new ArrayList<>();
    private List<RelationshipInfo> relationships = new ArrayList<>();
    private List<ForeshadowingInfo> foreshadowings = new ArrayList<>();

    @Builder
    public AnalysisCallbackDTO(String jobId, UUID projectId, UUID documentId, String status, String errorMessage) {
        this.jobId = jobId;
        this.projectId = projectId;
        this.documentId = documentId;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    @Getter
    @NoArgsConstructor
    public static class CharacterInfo {
        private String name;
        private String description;
        private String role;
        private Map<String, Object> attributes;

        @Builder
        public CharacterInfo(String name, String description, String role, Map<String, Object> attributes) {
            this.name = name;
            this.description = description;
            this.role = role;
            this.attributes = attributes;
        }
    }

    @Getter
    @NoArgsConstructor
    public static class RelationshipInfo {
        private String sourceCharacter;
        private String targetCharacter;
        private String relationshipType;
        private String description;

        @Builder
        public RelationshipInfo(String sourceCharacter, String targetCharacter, String relationshipType, String description) {
            this.sourceCharacter = sourceCharacter;
            this.targetCharacter = targetCharacter;
            this.relationshipType = relationshipType;
            this.description = description;
        }
    }

    @Getter
    @NoArgsConstructor
    public static class ForeshadowingInfo {
        private String title;
        private String hint;
        private String revelation;
        private Integer chapterHint;
        private Integer chapterRevelation;

        @Builder
        public ForeshadowingInfo(String title, String hint, String revelation, Integer chapterHint, Integer chapterRevelation) {
            this.title = title;
            this.hint = hint;
            this.revelation = revelation;
            this.chapterHint = chapterHint;
            this.chapterRevelation = chapterRevelation;
        }
    }
}
