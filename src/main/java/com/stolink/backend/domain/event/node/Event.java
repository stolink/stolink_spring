package com.stolink.backend.domain.event.node;

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
 * AI 분석으로 추출된 이벤트/장면 노드 (Neo4j)
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

    @Property("projectId")
    private String projectId;

    // AI 생성 ID (예: E001)
    @Property("eventId")
    private String eventId;

    @Property("eventType")
    private String eventType;

    @Property("narrativeSummary")
    private String narrativeSummary;

    @Property("description")
    private String description;

    @Property("locationRef")
    private String locationRef;

    @Property("prevEventId")
    private String prevEventId;

    @Property("visualScene")
    private String visualScene;

    @Property("cameraAngle")
    private String cameraAngle;

    @Property("importance")
    private Integer importance;

    @Property("isForeshadowing")
    @Builder.Default
    private Boolean isForeshadowing = false;

    @Property("chapterRef")
    private Integer chapterRef;

    // New fields from callback_result.json
    @Property("chapter")
    private Integer chapter;

    @Property("sequenceOrder")
    private Integer sequenceOrder;

    @Property("documentId")
    private String documentId;

    // JSON fields
    @Property("participantsJson")
    private String participantsJson; // JSON array of character names

    @Property("timestampJson")
    private String timestampJson; // { relative, absolute, chapter, sequence_order }

    @Property("changesJson")
    private String changesJson; // changes_made field

    @Property("embeddingJson")
    private String embeddingJson; // 3072-dim vector
}
