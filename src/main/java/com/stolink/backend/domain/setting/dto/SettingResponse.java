package com.stolink.backend.domain.setting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stolink.backend.domain.setting.node.Setting;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SettingResponse {
    private String id;

    @JsonProperty("setting_id")
    private String settingId;

    private String name;

    @JsonProperty("location_name")
    private String locationName;

    @JsonProperty("location_type")
    private String locationType;

    @JsonProperty("visual_background")
    private String visualBackground;

    private String atmosphere;

    @JsonProperty("time_of_day")
    private String timeOfDay;

    private String lighting;
    private String weather;
    private String description;
    private String significance;

    @JsonProperty("is_primary")
    private Boolean isPrimary;

    @JsonProperty("notable_features")
    private java.util.List<String> notableFeatures;

    public static SettingResponse from(Setting setting) {
        return SettingResponse.builder()
                .id(setting.getId())
                .settingId(setting.getSettingId())
                .name(setting.getName())
                .locationName(setting.getName())
                .locationType(setting.getLocationType() != null ? setting.getLocationType().toLowerCase() : null)
                .visualBackground(setting.getVisualBackground())
                .atmosphere(setting.getAtmosphere())
                .timeOfDay(setting.getTimeOfDay())
                .lighting(setting.getLighting())
                .weather(setting.getWeather())
                .description(setting.getDescription())
                .significance(setting.getStorySignificance())
                .isPrimary(setting.getIsPrimary())
                .notableFeatures(setting.getNotableFeatures())
                .build();
    }
}
