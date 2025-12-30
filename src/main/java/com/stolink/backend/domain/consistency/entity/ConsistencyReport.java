package com.stolink.backend.domain.consistency.entity;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * AI 분석 일관성 보고서
 */
@Entity
@Table(name = "consistency_reports")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ConsistencyReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "job_id", length = 100)
    private String jobId;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "requires_reextraction")
    @Builder.Default
    private Boolean requiresReextraction = false;

    // conflicts (JSON array)
    @Column(name = "conflicts_json", columnDefinition = "TEXT")
    private String conflictsJson;

    // warnings (JSON array)
    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

    // resolution_summary (JSON)
    @Column(name = "resolution_summary_json", columnDefinition = "TEXT")
    private String resolutionSummaryJson;

    // neo4j_validation (JSON)
    @Column(name = "neo4j_validation_json", columnDefinition = "TEXT")
    private String neo4jValidationJson;
}
