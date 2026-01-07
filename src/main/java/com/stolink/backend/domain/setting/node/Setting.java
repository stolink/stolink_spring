package com.stolink.backend.domain.setting.node;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 분석으로 추출된 장소/배경 설정 노드 (Neo4j)
 */
@Node("Setting")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Setting {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;

    @Property("projectId")
    private String projectId;

    // AI 생성 ID (예: loc_forest_01)
    @Property("settingId")
    private String settingId;

    @Property("name")
    private String name;

    @Property("locationName")
    private String locationName;

    @Property("locationType")
    private String locationType; // FOREST, CITY, CASTLE, etc.

    @Property("visualPrompt")
    private String visualPrompt;

    @Property("visualBackground")
    private String visualBackground;

    @Property("timeOfDay")
    private String timeOfDay;

    @Property("lightingDescription")
    private String lightingDescription;

    @Property("atmosphereKeywords")
    private String atmosphereKeywords;

    @Property("weatherCondition")
    private String weatherCondition;

    @Property("artStyle")
    private String artStyle;

    @Property("description")
    private String description;

    @Property("isPrimaryLocation")
    @Builder.Default
    private Boolean isPrimaryLocation = false;

    @Property("storySignificance")
    private String storySignificance;

    // New fields from callback_result.json
    @Property("parentLocation")
    private String parentLocation;

    @Property("firstMentioned")
    private String firstMentioned;

    // JSON field for complex data
    @Property("staticObjectsJson")
    private String staticObjectsJson; // notable_features as JSON array

    @Property("embeddingJson")
    private String embeddingJson; // 3072-dim vector
}
