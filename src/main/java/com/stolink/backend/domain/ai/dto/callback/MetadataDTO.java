package com.stolink.backend.domain.ai.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Metadata DTO - AI 분석 결과의 메타데이터
 * Python 콜백의 metadata 객체에 대응
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetadataDTO {

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("model_version")
    private String modelVersion;

    @JsonProperty("pipeline_version")
    private String pipelineVersion;
}
