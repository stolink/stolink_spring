package com.stolink.backend.domain.setting.dto;

import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 설정(장소) 이미지 생성 요청 DTO
 */
public record SettingImageGenerationRequest(
        @NotBlank(message = "visual_background는 필수입니다")
        @JsonProperty("visual_background")
        String visualBackground,

        @JsonProperty("atmosphere")
        String atmosphere,

        @JsonProperty("lighting")
        String lighting,

        @JsonProperty("time_of_day")
        String timeOfDay,

        @JsonProperty("art_style")
        String artStyle,

        @JsonProperty("action")
        String action
) {
}
