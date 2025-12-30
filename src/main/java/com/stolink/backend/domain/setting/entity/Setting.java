package com.stolink.backend.domain.setting.entity;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * AI 분석으로 추출된 장소/배경 설정 엔티티
 */
@Entity
@Table(name = "settings")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Setting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "setting_id", length = 50)
    private String settingId; // AI가 생성한 설정 ID (loc_forest_01 등)

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", length = 30)
    private LocationType locationType;

    @Column(name = "visual_prompt", columnDefinition = "TEXT")
    private String visualPrompt;

    @Column(name = "visual_background", columnDefinition = "TEXT")
    private String visualBackground; // 이미지 생성용 배경 프롬프트

    @Column(name = "time_of_day", length = 30)
    private String timeOfDay;

    @Column(name = "lighting_description", columnDefinition = "TEXT")
    private String lightingDescription;

    @Column(name = "atmosphere_keywords", columnDefinition = "TEXT")
    private String atmosphereKeywords; // 쉼표 구분

    @Column(name = "weather_condition", length = 30)
    private String weatherCondition;

    @Column(name = "art_style", length = 100)
    private String artStyle; // 아트 스타일

    @Column(columnDefinition = "TEXT")
    private String description; // 장소 설명

    @Column(name = "static_objects", columnDefinition = "TEXT")
    private String staticObjects; // JSON array string (notable_features)

    @Column(name = "is_primary_location")
    @Builder.Default
    private Boolean isPrimaryLocation = false;

    @Column(name = "story_significance", columnDefinition = "TEXT")
    private String storySignificance;

    public enum LocationType {
        FOREST, CITY, CASTLE, VILLAGE, MOUNTAIN, OCEAN, DESERT,
        CAVE, TEMPLE, BATTLEFIELD, TAVERN, PALACE, SHIP, OTHER
    }
}
