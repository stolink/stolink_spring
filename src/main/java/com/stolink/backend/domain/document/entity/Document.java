package com.stolink.backend.domain.document.entity;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
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
    @Builder.Default
    private String content = "";

    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private String synopsis = "";

    @Column(name = "\"order\"", nullable = false)
    @Builder.Default
    private Integer order = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.DRAFT;

    @Column(length = 50)
    private String label;

    @Column(length = 7)
    private String labelColor;

    @Column(nullable = false)
    @Builder.Default
    private Integer wordCount = 0;

    private Integer targetWordCount;

    @Column(nullable = false)
    @Builder.Default
    private Boolean includeInCompile = true;

    @Column(columnDefinition = "text")
    private String keywords; // Comma-separated tags

    @Column(columnDefinition = "TEXT")
    private String notes;

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
}
