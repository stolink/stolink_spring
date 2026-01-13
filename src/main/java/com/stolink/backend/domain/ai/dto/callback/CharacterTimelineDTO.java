package com.stolink.backend.domain.ai.dto.callback;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Character Timeline DTO for AI callback
 * 캐릭터의 챕터별 상태 변화 추적
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CharacterTimelineDTO {

    @JsonProperty("character_name")
    private String characterName;

    private Integer chapter;

    @JsonProperty("current_location")
    private String currentLocation;

    @JsonProperty("emotional_state")
    private String emotionalState;

    @JsonProperty("health_status")
    private String healthStatus;

    @JsonProperty("state_changes")
    @Builder.Default
    private Map<String, String> stateChanges = new HashMap<>();
}
