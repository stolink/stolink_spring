package com.stolink.backend.domain.ai.dto.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * 기존 장소/설정 참조 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExistingSettingRef {
    private String id;
    private String name;

    @JsonProperty("location_type")
    private String locationType;
}
