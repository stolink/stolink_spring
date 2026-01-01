package com.stolink.backend.domain.event.entity;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * AI 분석으로 추출된 이벤트/장면 엔티티
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "event_id", length = 50)
    private String eventId; // AI가 생성한 이벤트 ID (E001 등)

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 30)
    private EventType eventType;

    @Column(name = "narrative_summary", columnDefinition = "TEXT")
    private String narrativeSummary;

    @Column(columnDefinition = "TEXT")
    private String description; // 이벤트 상세 설명

    @Column(columnDefinition = "TEXT")
    private String participants; // JSON array string

    @Column(name = "location_ref", length = 100)
    private String locationRef;

    @Column(name = "prev_event_id", length = 50)
    private String prevEventId;

    @Column(name = "visual_scene", columnDefinition = "TEXT")
    private String visualScene;

    @Column(name = "camera_angle", length = 50)
    private String cameraAngle;

    @Column
    private Integer importance;

    @Column(name = "is_foreshadowing")
    @Builder.Default
    private Boolean isForeshadowing = false;

    @Column(name = "chapter_ref")
    private Integer chapterRef;

    // New fields for AI schema
    @Column(name = "timestamp_json", columnDefinition = "TEXT")
    private String timestampJson; // { relative, absolute, chapter, sequence_order }

    @Column(name = "changes_json", columnDefinition = "TEXT")
    private String changesJson; // changes_made field

    @Column(name = "embedding_json", columnDefinition = "TEXT")
    private String embeddingJson; // 1024-dim vector

    public enum EventType {
        ACTION, DIALOGUE, EMOTION, DISCOVERY, DECISION, FLASHBACK,
        TRANSITION, CONFLICT, RESOLUTION, APPEARANCE, CONFRONTATION
    }
}
