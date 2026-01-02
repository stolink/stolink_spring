package com.stolink.backend.domain.setting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stolink.backend.domain.setting.entity.Setting;
import lombok.Builder;
import lombok.Getter;
import java.util.UUID;

@Getter
@Builder
public class SettingResponse {
    private UUID id;

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

    private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public static SettingResponse from(Setting setting) {
        java.util.List<String> features = java.util.Collections.emptyList();
        if (setting.getStaticObjects() != null) {
            try {
                features = objectMapper.readValue(setting.getStaticObjects(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
                        });
            } catch (Exception e) {
                // ignore
            }
        }

        return SettingResponse.builder()
                .id(setting.getId())
                .settingId(setting.getSettingId())
                .name(setting.getName())
                .locationName(setting.getLocationName())
                .locationType(setting.getLocationType() != null ? setting.getLocationType().name().toLowerCase() : null)
                .visualBackground(setting.getVisualBackground())
                .atmosphere(setting.getAtmosphereKeywords())
                .timeOfDay(setting.getTimeOfDay())
                .lighting(setting.getLightingDescription())
                .weather(setting.getWeatherCondition())
                .description(setting.getDescription())
                .significance(setting.getStorySignificance())
                .isPrimary(setting.getIsPrimaryLocation())
                .notableFeatures(features)
                .build();
    }
}
