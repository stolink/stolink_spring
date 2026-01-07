package com.stolink.backend.domain.ai.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Event DTO - AI 분석 결과의 이벤트 데이터
 * callback_result.json의 events 배열 요소에 대응
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventDTO {

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("narrative_summary")
    private String narrativeSummary;

    private String description;

    private List<String> participants;

    @JsonProperty("location_ref")
    private String locationRef;

    @JsonProperty("prev_event_id")
    private String prevEventId;

    private java.util.Map<String, Object> timestamp;

    private Integer importance;

    @JsonProperty("changes_made")
    private java.util.Map<String, Object> changesMade;

    private List<Double> embedding;

    private Integer chapter;

    @JsonProperty("sequence_order")
    private Integer sequenceOrder;

    @JsonProperty("document_id")
    private String documentId;

    @JsonProperty("visual_scene")
    private String visualScene;

    @JsonProperty("camera_angle")
    private String cameraAngle;

    @JsonProperty("is_foreshadowing")
    private Boolean isForeshadowing;
}
