package com.stolink.backend.domain.ai.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 분석 결과 콜백 DTO (Multi-Agent 파이프라인 결과)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisCallbackDTO {

    @JsonAlias("job_id")
    private String jobId;

    private String status; // "completed", "warning", "failed" (case-insensitive)

    // JSON에서는 "results" (복수형)으로 옴, but handling "result" alias too
    @JsonProperty("results")
    @JsonAlias("result")
    private Map<String, Object> results;

    private String error;

    // ✅ 메타데이터 필드 추가
    @JsonAlias("processing_time_ms")
    private Integer processingTimeMs;

    @JsonAlias("trace_id")
    private String traceId;

    public Map<String, Object> getResult() {
        return results;
    }

    public void setResult(Map<String, Object> result) {
        this.results = result;
    }

    /**
     * 성공 여부 확인
     */
    public boolean isSuccess() {
        if (status == null)
            return false;
        String s = status.toLowerCase();
        return "completed".equals(s) || "warning".equals(s);
    }

    /**
     * 실패 여부 확인
     */
    public boolean isFailed() {
        if (status == null)
            return false;
        return "failed".equalsIgnoreCase(status);
    }
}
