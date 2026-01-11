package com.stolink.backend.domain.ai.dto;

import java.time.LocalDateTime;

/**
 * 프로젝트별 분석 작업 상태 응답 DTO
 */
public record ProjectAnalysisJobResponse(
        String jobId,
        String status,
        Integer progress,
        LocalDateTime lastCompletedAt) {
}
