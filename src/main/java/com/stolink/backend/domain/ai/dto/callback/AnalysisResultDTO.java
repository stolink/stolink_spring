package com.stolink.backend.domain.ai.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * AnalysisResult DTO - AI 분석 결과의 result 객체
 * Python 콜백의 result 필드에 대응
 *
 * 구조:
 * {
 *   "characters": [...],
 *   "events": [...],
 *   "settings": [...],
 *   "relationships": [...],
 *   "plot": { "summary": "...", "foreshadowing": [...] },
 *   "consistency_report": { "score": 95, "conflicts": [...] },
 *   "validation": { "is_valid": true, "quality_score": 98 },
 *   "metadata": { "processing_time_ms": 1234 }
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResultDTO {

    private List<CharacterDTO> characters;

    private List<EventDTO> events;

    private List<SettingDTO> settings;

    private List<RelationshipDTO> relationships;

    /**
     * Plot data - Python sends as "plot"
     */
    private PlotDTO plot;

    /**
     * Consistency report - Python sends as "consistency_report"
     */
    @JsonProperty("consistency_report")
    private ConsistencyReportDTO consistencyReport;

    /**
     * Validation result
     */
    private ValidationDTO validation;

    /**
     * Processing metadata
     */
    private MetadataDTO metadata;

    /**
     * Sections (semantic chunks) - optional, depends on analysis type
     */
    private List<SectionDTO> sections;

    /**
     * Emotions data - character emotional states
     */
    private Map<String, Object> emotions;
}
