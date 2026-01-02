package com.stolink.backend.domain.document.entity;

import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Section 엔티티 - 의미적 분할 단위
 * 
 * AI Backend에서 Semantic Chunking으로 생성된 Section을 저장합니다.
 * Document(TEXT)와 1:N 관계를 가집니다.
 */
@Entity
@Table(name = "sections", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "document_id", "sequence_order" })
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Section extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private Integer sequenceOrder;

    @Column(length = 200)
    private String navTitle;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "vector(1024)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] embedding;

    /**
     * 관련 캐릭터 이름 목록
     */
    @Column(columnDefinition = "TEXT")
    private String relatedCharactersJson;

    /**
     * 관련 이벤트 ID 목록
     */
    @Column(columnDefinition = "TEXT")
    private String relatedEventsJson;

    @Builder
    public Section(UUID id, Document document, Integer sequenceOrder, String navTitle,
            String content, float[] embedding, String relatedCharactersJson, String relatedEventsJson) {
        this.id = id;
        this.document = document;
        this.sequenceOrder = sequenceOrder;
        this.navTitle = navTitle;
        this.content = content;
        this.embedding = embedding;
        this.relatedCharactersJson = relatedCharactersJson;
        this.relatedEventsJson = relatedEventsJson;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void updateNavTitle(String navTitle) {
        this.navTitle = navTitle;
    }

    public void updateEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public void updateRelatedCharacters(String relatedCharactersJson) {
        this.relatedCharactersJson = relatedCharactersJson;
    }

    public void updateRelatedEvents(String relatedEventsJson) {
        this.relatedEventsJson = relatedEventsJson;
    }
}
