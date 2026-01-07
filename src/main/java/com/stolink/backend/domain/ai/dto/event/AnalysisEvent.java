package com.stolink.backend.domain.ai.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stolink.backend.domain.ai.dto.callback.CharacterDTO;
import com.stolink.backend.domain.ai.dto.callback.ConsistencyReportDTO;
import com.stolink.backend.domain.ai.dto.callback.EventDTO;
import com.stolink.backend.domain.ai.dto.callback.PlotDTO;
import com.stolink.backend.domain.ai.dto.callback.RelationshipDTO;
import com.stolink.backend.domain.ai.dto.callback.SectionDTO;
import com.stolink.backend.domain.ai.dto.callback.SettingDTO;
import com.stolink.backend.domain.ai.dto.callback.ValidationDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RabbitMQ Event Sourcing용 분석 이벤트 DTO
 * 
 * AI Backend가 발행하는 분석 완료/실패 이벤트 구조.
 * 기존 AnalysisCallbackDTO와 유사하나 event_id로 idempotency 보장.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisEvent {

    // ============================================================
    // Event Metadata
    // ============================================================

    @JsonProperty("event_type")
    private String eventType; // "ANALYSIS_COMPLETED" | "ANALYSIS_FAILED"

    @JsonProperty("event_id")
    private String eventId; // UUID (Idempotency key)

    private Instant timestamp;

    // ============================================================
    // Analysis Target Identifiers
    // ============================================================

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("document_id")
    private String documentId;

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("parent_folder_id")
    private String parentFolderId;

    @JsonProperty("trace_id")
    private String traceId;

    // ============================================================
    // Analysis Results (ANALYSIS_COMPLETED only)
    // ============================================================

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

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;

    // ============================================================
    // Error Information (ANALYSIS_FAILED only)
    // ============================================================

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("error_details")
    private Map<String, Object> errorDetails;

    // ============================================================
    // Helper Methods
    // ============================================================

    public boolean isSuccess() {
        return "ANALYSIS_COMPLETED".equals(eventType);
    }

    public boolean isFailed() {
        return "ANALYSIS_FAILED".equals(eventType);
    }
}
