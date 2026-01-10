package com.stolink.backend.domain.setting.node;

import java.util.List;

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
 * AI 분석 결과(callback JSON)를 1:1로 매핑하여 그래프 데이터베이스에 저장합니다.
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

    // AI 생성 ID (예: loc_steel_fortress_01)
    @Property("settingId")
    private String settingId;

    @Property("name")
    private String name;

    @Property("locationType")
    private String locationType; // castle, room, etc.

    @Property("parentLocation")
    private String parentLocation; // 상위 장소 이름

    @Property("visualBackground")
    private String visualBackground;

    @Property("atmosphere")
    private String atmosphere; // 예: "웅장한, 견고한, 엄숙한..."

    @Property("timeOfDay")
    private String timeOfDay; // night, day

    @Property("lighting")
    private String lighting;

    @Property("weather")
    private String weather;

    @Property("artStyle")
    private String artStyle;

    @Property("description")
    private String description;

    // AI의 notable_features 배열을 Neo4j의 List<String> 프로퍼티로 저장
    @Property("notableFeatures")
    private List<String> notableFeatures;

    @Property("significance")
    private String storySignificance;

    @Property("isPrimary")
    @Builder.Default
    private Boolean isPrimary = false;

    // 추가 정보 (JSON에는 없지만 관리를 위해 필요한 필드)
    @Property("firstMentioned")
    private String firstMentioned;

    @Property("embeddingJson")
    private String embeddingJson; // 추후 검색용 벡터
}
