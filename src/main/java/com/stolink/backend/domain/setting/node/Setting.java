package com.stolink.backend.domain.setting.node;

import lombok.*;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

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

    private String name;
    private String locationName;
    private String locationType; // FOREST, CITY, CASTLE, etc.

    private String visualPrompt;
    private String visualBackground;
    private String timeOfDay;
    private String lightingDescription;
    private String atmosphereKeywords;
    private String weatherCondition;
    private String artStyle;
    private String description;

    @Builder.Default
    private Boolean isPrimaryLocation = false;

    private String storySignificance;

    // JSON field for complex data
    private String staticObjectsJson; // notable_features as JSON array
}
