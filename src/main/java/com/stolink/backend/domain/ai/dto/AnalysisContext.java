package com.stolink.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stolink.backend.domain.ai.dto.context.*;
import lombok.*;

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

    @JsonProperty("previous_chapters")
    private List<String> previousChapters;

    @JsonProperty("existing_characters")
    private List<ExistingCharacterRef> existingCharacters;

    @JsonProperty("existing_events")
    private List<ExistingEventRef> existingEvents;

    @JsonProperty("existing_relationships")
    private List<ExistingRelationshipRef> existingRelationships;

    @JsonProperty("existing_settings")
    private List<ExistingSettingRef> existingSettings;

    @JsonProperty("world_rules_summary")
    private String worldRulesSummary;
}
