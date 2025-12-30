package com.stolink.backend.domain.character.node;

import lombok.*;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import com.stolink.backend.domain.character.relationship.CharacterRelationship;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node("Character")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Character {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;

    private String projectId;
    private String name;
    private String faction;
    private String role; // protagonist, antagonist, supporting, mentor, sidekick, other
    private String imageUrl;
    private String status; // alive, dead, unknown

    // JSON 문자열로 저장 (Neo4j는 중첩 Map을 지원하지 않음)
    private String visualJson;
    private String personalityJson;
    private String currentMoodJson;

    // 추가 필드 (v2.0 스키마)
    private String motivation; // 캐릭터 동기
    private String firstAppearance; // 첫 등장 장소

    // extras를 단일 JSON 문자열로 저장
    private String extrasJson;

    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private List<CharacterRelationship> relationships = new ArrayList<>();
}
