package com.stolink.backend.domain.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 배치 재발송 요청 DTO
 * 
 * AI Backend에서 타임아웃된 배치의 누락 문서 재발송을 요청할 때 사용됩니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchRetryRequest {

    /**
     * 타임아웃된 배치 ID
     */
    @NotBlank(message = "batch_id는 필수입니다")
    @JsonProperty("batch_id")
    private String batchId;

    /**
     * 프로젝트 ID
     */
    @NotBlank(message = "project_id는 필수입니다")
    @JsonProperty("project_id")
    private String projectId;

    /**
     * 도착하지 않은 문서 순서 번호 목록
     */
    @NotNull(message = "missing_document_orders는 필수입니다")
    @JsonProperty("missing_document_orders")
    private List<Integer> missingDocumentOrders;

    /**
     * 액션 유형: "RETRY_OR_CANCEL"
     */
    @JsonProperty("action")
    @Builder.Default
    private String action = "RETRY_OR_CANCEL";
}
