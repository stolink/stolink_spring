package com.stolink.backend.domain.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 배치 재발송 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchRetryResponse {

    /**
     * 처리 상태: "RETRY" 또는 "CANCELLED"
     */
    @JsonProperty("status")
    private String status;

    /**
     * 배치 ID
     */
    @JsonProperty("batch_id")
    private String batchId;

    /**
     * 재발송된 문서 순서 번호 목록 (status가 RETRY인 경우)
     */
    @JsonProperty("retried_orders")
    private List<Integer> retriedOrders;

    /**
     * 오류 메시지 (status가 CANCELLED인 경우)
     */
    @JsonProperty("error_message")
    private String errorMessage;

    // 팩토리 메서드
    public static BatchRetryResponse retry(String batchId, List<Integer> retriedOrders) {
        return BatchRetryResponse.builder()
                .status("RETRY")
                .batchId(batchId)
                .retriedOrders(retriedOrders)
                .build();
    }

    public static BatchRetryResponse cancelled(String batchId, String errorMessage) {
        return BatchRetryResponse.builder()
                .status("CANCELLED")
                .batchId(batchId)
                .errorMessage(errorMessage)
                .build();
    }
}
