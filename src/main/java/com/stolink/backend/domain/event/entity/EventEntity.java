package com.stolink.backend.domain.event.entity;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

/**
 * AI 서버 호환용 이벤트 엔티티 (PostgreSQL)
 * AI 서버가 events 테이블에 직접 데이터를 저장하므로 추가함.
 */
@Entity
@Table(name = "events")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class EventEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // AI 생성 ID (예: evt_01)
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "event_type", length = 50)
    private String eventType;

    // JSONB field for participants
    @Column(name = "participants", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String participants;

    @Column(name = "start_time", length = 100)
    private String startTime;

    @Column(name = "end_time", length = 100)
    private String endTime;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "importance_score")
    private Double importanceScore;

    @Column(name = "plot_relevance", columnDefinition = "TEXT")
    private String plotRelevance;

    @Column(columnDefinition = "TEXT")
    private String cause;

    @Column(columnDefinition = "TEXT")
    private String effect;

    // AI Analysis Fields from expected_result.json
    @Column(name = "chapter")
    private Integer chapter;

    @Column(name = "sequence_order")
    private Integer sequenceOrder;

    @Column(name = "narrative_summary", columnDefinition = "TEXT")
    private String narrativeSummary;

    @Column(name = "prev_event_id", length = 100)
    private String prevEventId;

    // Document reference (required by DB constraint)
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    // --- 편의 메서드 ---
    public void updateDetails(String description, String eventType, String participants,
            String startTime, String endTime, String location,
            Double importanceScore, String plotRelevance, String cause, String effect,
            Integer chapter, Integer sequenceOrder, String narrativeSummary, String prevEventId) {
        this.description = description;
        this.eventType = eventType;
        this.participants = participants;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.importanceScore = importanceScore;
        this.plotRelevance = plotRelevance;
        this.cause = cause;
        this.effect = effect;
        this.chapter = chapter;
        this.sequenceOrder = sequenceOrder;
        this.narrativeSummary = narrativeSummary;
        this.prevEventId = prevEventId;
    }
}
