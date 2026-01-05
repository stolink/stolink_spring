package com.stolink.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stolink.backend.domain.ai.dto.callback.*;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * AI 분석 결과 콜백 DTO (Multi-Agent 파이프라인 결과)
 *
 * Python AI Agent가 전송하는 JSON 구조:
 * {
 *   "jobId": "...",
 *   "status": "COMPLETED",
 *   "result": {
 *     "characters": [...],
 *     "events": [...],
 *     "settings": [...],
 *     "relationships": [{ "source": "Name A", "target": "Name B", "relation_type": "FRIEND", ... }],
 *     "plot": { "summary": "...", "foreshadowing": [...] },
 *     "consistency_report": { "score": 95, "conflicts": [...] },
 *     "validation": { "is_valid": true, "quality_score": 98 },
 *     "metadata": { "processing_time_ms": 1234 }
 *   }
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisCallbackDTO {

    @JsonAlias("jobId")
    @JsonProperty("jobId")
    private String jobId;

    private String status; // "COMPLETED", "FAILED", "WARNING"

    private String error;

    /**
     * Nested result object containing all analysis data
     * This is the PRIMARY field - Python sends data here
     */
    private AnalysisResultDTO result;

    // ============================================================
    // Backward compatibility fields (flat structure, deprecated)
    // These are used if 'result' is null
    // ============================================================

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;

    @JsonProperty("trace_id")
    private String traceId;

    private List<SectionDTO> sections;

    private List<CharacterDTO> characters;

    private List<EventDTO> events;

    private List<SettingDTO> settings;

    private List<RelationshipDTO> relationships;

    @JsonProperty("plot_integration")
    private PlotDTO plotIntegration;

    @JsonProperty("consistency_report")
    private ConsistencyReportDTO consistencyReport;

    private ValidationDTO validation;

    private Map<String, Object> emotions;

    // ============================================================
    // Unified Getters (handle both nested and flat structures)
    // ============================================================

    /**
     * Get characters from nested result or flat structure
     */
    public List<CharacterDTO> getEffectiveCharacters() {
        if (result != null && result.getCharacters() != null) {
            return result.getCharacters();
        }
        return characters;
    }

    /**
     * Get events from nested result or flat structure
     */
    public List<EventDTO> getEffectiveEvents() {
        if (result != null && result.getEvents() != null) {
            return result.getEvents();
        }
        return events;
    }

    /**
     * Get settings from nested result or flat structure
     */
    public List<SettingDTO> getEffectiveSettings() {
        if (result != null && result.getSettings() != null) {
            return result.getSettings();
        }
        return settings;
    }

    /**
     * Get relationships from nested result or flat structure
     */
    public List<RelationshipDTO> getEffectiveRelationships() {
        if (result != null && result.getRelationships() != null) {
            return result.getRelationships();
        }
        return relationships;
    }

    /**
     * Get plot data from nested result or flat structure
     */
    public PlotDTO getEffectivePlot() {
        if (result != null && result.getPlot() != null) {
            return result.getPlot();
        }
        return plotIntegration;
    }

    /**
     * Get consistency report from nested result or flat structure
     */
    public ConsistencyReportDTO getEffectiveConsistencyReport() {
        if (result != null && result.getConsistencyReport() != null) {
            return result.getConsistencyReport();
        }
        return consistencyReport;
    }

    /**
     * Get validation from nested result or flat structure
     */
    public ValidationDTO getEffectiveValidation() {
        if (result != null && result.getValidation() != null) {
            return result.getValidation();
        }
        return validation;
    }

    /**
     * Get sections from nested result or flat structure
     */
    public List<SectionDTO> getEffectiveSections() {
        if (result != null && result.getSections() != null) {
            return result.getSections();
        }
        return sections;
    }

    /**
     * Get emotions from nested result or flat structure
     */
    public Map<String, Object> getEffectiveEmotions() {
        if (result != null && result.getEmotions() != null) {
            return result.getEmotions();
        }
        return emotions;
    }

    /**
     * Get processing time from metadata or flat structure
     */
    public Long getEffectiveProcessingTimeMs() {
        if (result != null && result.getMetadata() != null && result.getMetadata().getProcessingTimeMs() != null) {
            return result.getMetadata().getProcessingTimeMs();
        }
        return processingTimeMs;
    }

    /**
     * Get trace ID from metadata or flat structure
     */
    public String getEffectiveTraceId() {
        if (result != null && result.getMetadata() != null && result.getMetadata().getTraceId() != null) {
            return result.getMetadata().getTraceId();
        }
        return traceId;
    }

    /**
     * 성공 여부 확인
     */
    public boolean isSuccess() {
        if (status == null)
            return false;
        String s = status.toLowerCase();
        return "completed".equals(s) || "warning".equals(s);
    }

    /**
     * 실패 여부 확인
     */
    public boolean isFailed() {
        if (status == null)
            return false;
        return "failed".equalsIgnoreCase(status);
    }
}
