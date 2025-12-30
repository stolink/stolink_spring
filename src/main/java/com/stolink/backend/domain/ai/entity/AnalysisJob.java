package com.stolink.backend.domain.ai.entity;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI 분석 작업 상태 관리 엔티티
 */
@Entity
@Table(name = "analysis_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AnalysisJob extends BaseEntity {

    @Id
    private String jobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "trace_id", length = 100)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    public void markAsProcessing() {
        this.status = JobStatus.PROCESSING;
        this.startedAt = LocalDateTime.now();
    }

    public void updateStatus(JobStatus newStatus) {
        this.status = newStatus;
    }

    public void updateStatus(JobStatus newStatus, String message) {
        this.status = newStatus;
        if (newStatus == JobStatus.FAILED) {
            this.errorMessage = message;
            this.completedAt = LocalDateTime.now();
        }
    }

    public void markAsCompleted(Long processingTimeMs) {
        this.status = JobStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.processingTimeMs = processingTimeMs;
    }

    public void markAsFailed(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    public enum JobStatus {
        PENDING, // 대기 중
        PROCESSING, // RabbitMQ로 전송됨
        ANALYZING, // AI 분석 중 (Agent 실행 중)
        VALIDATING, // 검증 중
        COMPLETED, // 완료
        FAILED // 실패
    }
}
