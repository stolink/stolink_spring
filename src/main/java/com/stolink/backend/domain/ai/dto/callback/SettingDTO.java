package com.stolink.backend.domain.ai.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Setting DTO - AI 분석 결과의 장소/배경 설정 데이터
 * callback_result.json의 settings 배열 요소에 대응
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettingDTO {

    @JsonProperty("setting_id")
    private String settingId;

    private String name;

    @JsonProperty("location_name")
    private String locationName;

    @JsonProperty("location_type")
    private String locationType;

    @JsonProperty("parent_location")
    private String parentLocation;

    @JsonProperty("visual_background")
    private String visualBackground;

    private String atmosphere;

    @JsonProperty("time_of_day")
    private String timeOfDay;

    private String lighting;

    private String weather;

    @JsonProperty("art_style")
    private String artStyle;

    private String description;

    @JsonProperty("notable_features")
    private List<String> notableFeatures;

    private String significance;

    @JsonProperty("first_mentioned")
    private String firstMentioned;

    @JsonProperty("is_primary")
    private Boolean isPrimary;

    private List<Double> embedding;
}
