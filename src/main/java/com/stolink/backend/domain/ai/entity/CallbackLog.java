package com.stolink.backend.domain.ai.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 콜백 처리 로그 (중복 처리 방지용)
 * 
 * AI 서버가 콜백을 여러 번 재시도할 수 있으므로,
 * jobId 기반으로 중복 콜백을 감지하여 무시합니다.
 */
@Entity
@Table(name = "callback_logs", indexes = {
        @Index(name = "idx_callback_logs_job_id", columnList = "job_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CallbackLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private String jobId;

    @Column(name = "message_type", length = 50)
    private String messageType;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "project_id")
    private UUID projectId;

    /**
     * 간편 생성자
     */
    public CallbackLog(String jobId, LocalDateTime processedAt) {
        this.jobId = jobId;
        this.processedAt = processedAt;
    }
}
