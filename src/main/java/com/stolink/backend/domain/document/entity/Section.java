package com.stolink.backend.domain.document.entity;

import java.sql.Timestamp;
import java.util.UUID;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 문서 섹션 엔티티 (RAG용)
 * 
 * 문서를 청크 단위로 분할하여 벡터 임베딩과 함께 저장합니다.
 */
@Entity
@Table(name = "sections", uniqueConstraints = {
                @UniqueConstraint(columnNames = { "document_id", "sequence_order" })
}, indexes = {
                @Index(name = "idx_sections_document_id", columnList = "document_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Section {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "document_id", nullable = false)
        private Document document;

        @Column(name = "sequence_order", nullable = false)
        private Integer sequenceOrder;

        @Column(name = "nav_title", length = 200)
        private String navTitle;

        @Column(nullable = false, columnDefinition = "TEXT")
        private String content;

        @Column(name = "content_hash", length = 16)
        private String contentHash;

        @Column(name = "embedding")
        @JdbcTypeCode(SqlTypes.VECTOR)
        @Array(length = 3072)
        private float[] embedding;

        @Column(name = "related_characters_json", columnDefinition = "TEXT")
        private String relatedCharactersJson;

        @Column(name = "related_events_json", columnDefinition = "TEXT")
        private String relatedEventsJson;

        @CreationTimestamp
        @Column(name = "created_at", nullable = false, updatable = false)
        private Timestamp createdAt;

        @UpdateTimestamp
        @Column(name = "updated_at")
        private Timestamp updatedAt;
}
