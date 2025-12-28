package com.stolink.backend.domain.ai.dto.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * 기존 이벤트 참조 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExistingEventRef {
    private String id;

    @JsonProperty("event_type")
    private String eventType;

    private String summary;
    private Integer chapter;
}
