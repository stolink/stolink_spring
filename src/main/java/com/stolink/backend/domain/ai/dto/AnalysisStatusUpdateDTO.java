package com.stolink.backend.domain.ai.dto;

import com.stolink.backend.domain.document.entity.Document.AnalysisStatus;
import lombok.*;

/**
 * 분석 상태 업데이트 요청 DTO
 * 
 * Python에서 PROCESSING 상태 전환 시 사용합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisStatusUpdateDTO {

    private AnalysisStatus status;

    private String traceId;
}
