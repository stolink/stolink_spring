package com.stolink.backend.domain.character.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// 이미지 생성 작업 추적 엔티티
// RabbitMQ 메시지 전송 실패 시 재시도 및 콜백 처리를 위한 영속화
@Entity
@Table(name = "image_generation_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageGenerationTask {
    
    // 작업 ID (UUID) - RabbitMQ 메시지의 jobId와 동일
    @Id
    @Column(length = 36, nullable = false)
    private String jobId;
    
    // 사용자 ID
    @Column(nullable = false)
    private UUID userId;
    
    // 프로젝트 ID
    @Column(nullable = false)
    private UUID projectId;
    
    // 캐릭터 ID
    @Column(nullable = false)
    private UUID characterId;
    
    // 캐릭터 설명 (프롬프트)
    @Column(columnDefinition = "TEXT")
    private String description;
    
    // 작업 상태: PENDING(생성됨) -> SENT(전송완료) -> PROCESSING(처리중) -> COMPLETED/FAILED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;
    
    // 생성된 이미지 URL (완료 시)
    @Column(length = 500)
    private String imageUrl;
    
    // 실패 사유 (실패 시)
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    // 재시도 횟수
    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
    
    // 최대 재시도 횟수
    private static final int MAX_RETRY_COUNT = 3;
    
    // 생성 시각
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // 수정 시각
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    // 작업 상태 열거형
    public enum TaskStatus {
        PENDING,      // 생성됨, 전송 대기
        SENT,         // RabbitMQ 전송 완료
        PROCESSING,   // FastAPI 처리 중
        COMPLETED,    // 완료
        FAILED        // 실패
    }
    
    // 재시도 가능 여부 확인
    public boolean canRetry() {
        return retryCount < MAX_RETRY_COUNT && 
               (status == TaskStatus.PENDING || status == TaskStatus.FAILED);
    }
    
    // 재시도 횟수 증가
    public void incrementRetryCount() {
        this.retryCount++;
    }
    
    // 전송 성공 처리
    public void markAsSent() {
        this.status = TaskStatus.SENT;
    }
    
    // 처리 중 상태로 변경
    public void markAsProcessing() {
        this.status = TaskStatus.PROCESSING;
    }
    
    // 완료 처리
    public void markAsCompleted(String imageUrl) {
        this.status = TaskStatus.COMPLETED;
        this.imageUrl = imageUrl;
    }
    
    // 실패 처리
    public void markAsFailed(String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
