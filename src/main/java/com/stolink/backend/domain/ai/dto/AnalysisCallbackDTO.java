package com.stolink.backend.domain.ai.dto;

import lombok.*;

import java.util.Map;

/**
 * AI 분석 결과 콜백 DTO (Multi-Agent 파이프라인 결과)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisCallbackDTO {

    private String jobId;
    private String status; // "COMPLETED", "WARNING", "FAILED"
    private Map<String, Object> result;
    private String error;

    /**
     * 성공 여부 확인
     */
    public boolean isSuccess() {
        return "COMPLETED".equals(status) || "WARNING".equals(status);
    }

    /**
     * 실패 여부 확인
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}
