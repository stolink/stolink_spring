package com.stolink.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * 글로벌 병합 요청 메시지 (2차 Pass 트리거)
 * 
 * 모든 문서 분석 완료 후 캐릭터/이벤트 병합을 위한 메시지입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalMergeMessage {

    @JsonProperty("message_type")
    @Builder.Default
    private String messageType = "GLOBAL_MERGE";

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("callback_url")
    private String callbackUrl;

    @JsonProperty("trace_id")
    private String traceId;
}
