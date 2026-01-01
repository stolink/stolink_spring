package com.stolink.backend.domain.document.entity;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Document parent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentType type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content = "";

    @Column(columnDefinition = "TEXT")
    private String synopsis = "";

    @Column(name = "\"order\"", nullable = false)
    private Integer order = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DocumentStatus status = DocumentStatus.DRAFT;

    // AI 분석 상태 (대용량 문서 분석 아키텍처)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AnalysisStatus analysisStatus = AnalysisStatus.NONE;

    @Column
    private Integer analysisRetryCount = 0;

    @Column(length = 50)
    private String label;

    @Column(length = 7)
    private String labelColor;

    @Column(nullable = false)
    private Integer wordCount = 0;

    private Integer targetWordCount;

    @Column(nullable = false)
    private Boolean includeInCompile = true;

    @Column(columnDefinition = "text")
    private String keywords; // Comma-separated tags

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Builder
    public Document(UUID id, Project project, Document parent, DocumentType type, String title, String content,
            String synopsis, Integer order, DocumentStatus status, String label, String labelColor, Integer wordCount,
            Integer targetWordCount, Boolean includeInCompile, String keywords, String notes) {
        this.id = id;
        this.project = project;
        this.parent = parent;
        this.type = type;
        this.title = title;
        this.content = content;
        this.synopsis = synopsis;
        this.order = order;
        this.status = status;
        this.label = label;
        this.labelColor = labelColor;
        this.wordCount = wordCount;
        this.targetWordCount = targetWordCount;
        this.includeInCompile = includeInCompile;
        this.keywords = keywords;
        this.notes = notes;
    }

    public void updateContent(String content) {
        this.content = content;
        this.wordCount = calculateWordCount(content);
    }

    public void update(String title, String synopsis, Integer order, DocumentStatus status,
            Integer targetWordCount, Boolean includeInCompile, String notes) {
        if (title != null)
            this.title = title;
        if (synopsis != null)
            this.synopsis = synopsis;
        if (order != null)
            this.order = order;
        if (status != null)
            this.status = status;
        if (targetWordCount != null)
            this.targetWordCount = targetWordCount;
        if (includeInCompile != null)
            this.includeInCompile = includeInCompile;
        if (notes != null)
            this.notes = notes;
    }

    public void updateLabel(String label, String labelColor) {
        if (label != null)
            this.label = label;
        if (labelColor != null)
            this.labelColor = labelColor;
    }

    public void updateKeywords(String keywords) {
        this.keywords = keywords;
    }

    /**
     * 문서의 부모를 변경합니다 (폴더 이동)
     * 
     * @param newParent 새로운 부모 문서 (null이면 루트로 이동)
     * @param newOrder  새 부모 아래에서의 순서
     */
    public void updateParent(Document newParent, int newOrder) {
        this.parent = newParent;
        this.order = newOrder;
    }

    private int calculateWordCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Simple word count - can be enhanced
        return text.replaceAll("<[^>]*>", "").trim().length();
    }

    public enum DocumentType {
        FOLDER, TEXT
    }

    public enum DocumentStatus {
        DRAFT, REVISED, FINAL
    }

    /**
     * AI 분석 상태 (대용량 문서 분석 아키텍처)
     */
    public enum AnalysisStatus {
        NONE, // 분석 요청 전
        PENDING, // 분석 대기
        QUEUED, // RabbitMQ 발행됨
        PROCESSING, // Python 처리 중
        COMPLETED, // 분석 완료
        FAILED // 분석 실패
    }

    // === 분석 상태 관리 메서드 ===

    public void updateAnalysisStatus(AnalysisStatus status) {
        this.analysisStatus = status;
    }

    public void incrementRetryCount() {
        this.analysisRetryCount++;
    }

    public void resetAnalysisForRetry() {
        this.analysisStatus = AnalysisStatus.QUEUED;
        this.analysisRetryCount++;
    }
}
