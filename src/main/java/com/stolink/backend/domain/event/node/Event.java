package com.stolink.backend.domain.event.node;

import lombok.*;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

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

    private String eventType; // ACTION, DIALOGUE, EMOTION, etc.
    private String narrativeSummary;
    private String description;
    private String locationRef;
    private String prevEventId;
    private String visualScene;
    private String cameraAngle;
    private Integer importance;

    @Builder.Default
    private Boolean isForeshadowing = false;

    private Integer chapterRef;

    // JSON fields
    private String participantsJson; // JSON array of character names
    private String timestampJson; // { relative, absolute, chapter, sequence_order }
    private String changesJson; // changes_made field
    private String embeddingJson; // 1024-dim vector
}
