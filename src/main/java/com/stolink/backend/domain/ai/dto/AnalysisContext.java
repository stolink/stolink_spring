package com.stolink.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stolink.backend.domain.ai.dto.context.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 분석 요청 시 기존 데이터 컨텍스트
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisContext {

    @JsonProperty("chapter_number")
    private Integer chapterNumber;

    @JsonProperty("total_chapters")
    private Integer totalChapters;

    @Builder.Default
    @JsonProperty("previous_chapters")
    private List<String> previousChapters = new ArrayList<>();

    @Builder.Default
    @JsonProperty("existing_characters")
    private List<ExistingCharacterRef> existingCharacters = new ArrayList<>();

    @Builder.Default
    @JsonProperty("existing_events")
    private List<ExistingEventRef> existingEvents = new ArrayList<>();

    @Builder.Default
    @JsonProperty("existing_relationships")
    private List<ExistingRelationshipRef> existingRelationships = new ArrayList<>();

    @Builder.Default
    @JsonProperty("existing_settings")
    private List<ExistingSettingRef> existingSettings = new ArrayList<>();

    @JsonProperty("world_rules_summary")
    private String worldRulesSummary;
}
