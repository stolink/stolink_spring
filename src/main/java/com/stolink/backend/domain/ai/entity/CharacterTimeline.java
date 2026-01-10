package com.stolink.backend.domain.ai.entity;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 맥락 유지 시스템: 캐릭터 타임라인 엔티티 (FastAPI AI Backend용)
 * 
 * 캐릭터의 상태 변화를 챕터별로 추적합니다.
 * Spring에서는 읽기 전용으로 사용합니다.
 * FastAPI AI Backend에서만 데이터를 씁니다.
 */
@Entity
@Table(name = "character_timeline", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "project_id", "character_name", "chapter" })
}, indexes = {
        @Index(name = "idx_timeline_character", columnList = "project_id, character_name"),
        @Index(name = "idx_timeline_chapter", columnList = "project_id, chapter")
})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CharacterTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "character_name", nullable = false, length = 255)
    private String characterName;

    @Column(nullable = false)
    private Integer chapter;

    @Column(name = "document_id")
    private UUID documentId;

    // 상태 추적 필드
    @Column(name = "health_status", length = 50)
    private String healthStatus;

    @Column(name = "emotional_state", length = 50)
    private String emotionalState;

    @Column(name = "current_location", length = 255)
    private String currentLocation;

    // 변화 기록 (JSONB)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state_changes", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> stateChanges = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;
}
