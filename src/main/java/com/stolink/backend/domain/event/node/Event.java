package com.stolink.backend.domain.event.node;

import java.util.List;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import com.stolink.backend.domain.character.node.Character;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Event Node Entity - 사건 (Neo4j Graph)
 *
 * AI 분석 결과(callback JSON)를 기반으로 1:1 매핑된 사건 노드입니다.
 * JSON 필드 구조를 그대로 따릅니다.
 */
@Node("Event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;

    @Property("project_id")
    private String projectId;

    // AI 생성 ID (예: E001)
    @Property("eventId")
    private String eventId;

    @Property("eventType")
    private String eventType; // action, etc.

    @Property("narrativeSummary")
    private String narrativeSummary;

    @Property("description")
    private String description;

    // JSON 배열을 Neo4j List<String>으로 저장
    // AI 분석 결과(callback JSON)에서 내려온 원본 리스트 (DB 속성)
    @Property("participants")
    private List<String> participants;

    // 검색 최적화를 위한 소문자 정규화 리스트
    @Property("participants_normalized")
    private List<String> participantsNormalized;

    // 그래프 관계를 통한 실제 참여자들 (INCOMING)
    @Relationship(type = "PARTICIPATES_IN", direction = Relationship.Direction.INCOMING)
    private List<Character> participantNodes;

    @Relationship(type = "PARTICIPATED_IN", direction = Relationship.Direction.INCOMING)
    private List<Character> participantNodesLegacy;

    @Property("locationRef")
    private String locationRef;

    @Property("prevEventId")
    private String prevEventId; // nullable

    @Property("timestamp")
    private String timestamp; // JSON object or string (nullable)

    @Property("importance")
    private Integer importance;

    // JSON 배열 [f1, f2, ...] -> Neo4j List<Double>
    @Property("embedding")
    private List<Double> embedding;

    @Property("chapter")
    private Integer chapter;

    @Property("sequenceOrder")
    private Integer sequenceOrder;

    @Property("documentId")
    private String documentId;
}
