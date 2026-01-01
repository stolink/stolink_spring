package com.stolink.backend.domain.ai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Job 상태 업데이트 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobStatusUpdateRequest {
    private String status;
    private String message;
}
