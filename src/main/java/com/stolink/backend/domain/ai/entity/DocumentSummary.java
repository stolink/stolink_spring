package com.stolink.backend.domain.ai.entity;

import java.sql.Timestamp;
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
 * AI 맥락 유지 시스템: 챕터 요약 엔티티 (FastAPI AI Backend용)
 * 
 * Spring에서는 읽기 전용으로 사용합니다.
 * FastAPI AI Backend에서만 데이터를 씁니다.
 */
@Entity
@Table(name = "document_summaries", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "document_id", "level" })
}, indexes = {
        @Index(name = "idx_summaries_project_level", columnList = "project_id, level")
})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DocumentSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /**
     * 요약 레벨
     * 1 = 전체 소설
     * 2 = 권/파트
     * 3 = 챕터 (기본값)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer level = 3;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "key_characters", columnDefinition = "text[]")
    private String[] keyCharacters;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "key_events", columnDefinition = "text[]")
    private String[] keyEvents;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;
}
