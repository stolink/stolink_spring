package com.stolink.backend.domain.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 분석 결과 검증 DTO
 * 
 * AI 서버에서 분석 결과에 대한 품질 평가 및 권장 조치를 포함합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValidationResult {

    /**
     * 분석 품질 점수 (0-100)
     */
    private Integer score;

    /**
     * 권장 조치
     * - "approve": 분석 결과 승인, 정상 완료 처리
     * - "retry": 자동 재분석 권장
     * - "manual_review": 관리자 검토 필요
     */
    private String action;

    /**
     * 발견된 문제점 목록
     */
    private List<String> issues;

    /**
     * 추가 메타데이터
     */
    @JsonProperty("metadata")
    private Object metadata;

    /**
     * 승인 상태 확인
     */
    public boolean isApproved() {
        return "approve".equalsIgnoreCase(action);
    }

    /**
     * 재시도 필요 여부 확인
     */
    public boolean needsRetry() {
        return "retry".equalsIgnoreCase(action);
    }

    /**
     * 수동 검토 필요 여부 확인
     */
    public boolean needsManualReview() {
        return "manual_review".equalsIgnoreCase(action);
    }
}
