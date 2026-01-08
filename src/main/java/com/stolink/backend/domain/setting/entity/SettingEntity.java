package com.stolink.backend.domain.setting.entity;

import java.util.UUID;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 설정(장소/배경) 엔티티 (PostgreSQL)
 * AI 서버와의 호환성을 위해 추가.
 */
@Entity
@Table(name = "settings")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SettingEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // AI 생성 ID (예: loc_forest_01)
    @Column(name = "setting_id", length = 100)
    private String settingId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "location_type", length = 255)
    private String locationType;

    @Column(name = "location_name", length = 100)
    private String locationName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "visual_prompt", columnDefinition = "TEXT")
    private String visualPrompt;

    @Column(name = "visual_background", columnDefinition = "TEXT")
    private String visualBackground;

    @Column(name = "time_of_day", length = 255)
    private String timeOfDay;

    @Column(name = "lighting_description", columnDefinition = "TEXT")
    private String lightingDescription;

    @Column(name = "atmosphere_keywords", columnDefinition = "TEXT")
    private String atmosphereKeywords;

    @Column(name = "weather_condition", length = 255)
    private String weatherCondition;

    @Column(name = "art_style", length = 100)
    private String artStyle;

    @Column(name = "is_primary_location")
    private Boolean isPrimaryLocation;

    @Column(name = "story_significance", columnDefinition = "TEXT")
    private String storySignificance;

    // JSON field for complex data
    @Column(name = "static_objects_json", columnDefinition = "TEXT")
    private String staticObjectsJson;

    // 추가 필드 (AI 콜백 완전 매핑용)
    @Column(name = "parent_location", length = 100)
    private String parentLocation;

    @Column(name = "first_mentioned", length = 100)
    private String firstMentioned;

    // --- 편의 메서드 ---
    public void updateDetails(String description, String visualPrompt, String visualBackground,
            String timeOfDay, String lightingDescription, String atmosphereKeywords,
            String weatherCondition, String artStyle, Boolean isPrimaryLocation,
            String storySignificance, String staticObjectsJson) {
        this.description = description;
        this.visualPrompt = visualPrompt;
        this.visualBackground = visualBackground;
        this.timeOfDay = timeOfDay;
        this.lightingDescription = lightingDescription;
        this.atmosphereKeywords = atmosphereKeywords;
        this.weatherCondition = weatherCondition;
        this.artStyle = artStyle;
        this.isPrimaryLocation = isPrimaryLocation;
        this.storySignificance = storySignificance;
        this.staticObjectsJson = staticObjectsJson;
    }
}
