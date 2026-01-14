package com.stolink.backend.domain.validation.entity;

import java.util.UUID;

import com.stolink.backend.global.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 분석 검증 결과
 */
@Entity
@Table(name = "validation_results")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ValidationResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "job_id", length = 100)
    private String jobId;

    @Column(name = "is_valid")
    @Builder.Default
    private Boolean isValid = true;

    @Column(name = "quality_score")
    private Integer qualityScore;

    @Column(length = 50)
    private String action; // approve, reject, etc.

    @Column(name = "action_description", columnDefinition = "TEXT")
    private String actionDescription;

    @Column(name = "average_completeness")
    private Double averageCompleteness;

    @Column(name = "error_count")
    @Builder.Default
    private Integer errorCount = 0;

    @Column(name = "warning_count")
    @Builder.Default
    private Integer warningCount = 0;

    // data_completeness (JSON)
    @Column(name = "data_completeness_json", columnDefinition = "TEXT")
    private String dataCompletenessJson;

    // validation_details (JSON)
    @Column(name = "validation_details_json", columnDefinition = "TEXT")
    private String validationDetailsJson;

    @Column(name = "execution_time_ms")
    private Double executionTimeMs;
}
