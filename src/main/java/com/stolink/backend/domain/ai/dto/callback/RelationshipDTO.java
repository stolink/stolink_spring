package com.stolink.backend.domain.ai.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Relationship DTO - AI 분석 결과의 캐릭터 관계 데이터
 * callback_result.json의 relationships 배열 요소에 대응
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelationshipDTO {

    private String source;

    private String target;

    @Builder.Default
    @JsonProperty("relation_types")
    private java.util.List<String> relationTypes = java.util.Collections.emptyList();

    private Integer strength;

    private String description;

    private Boolean bidirectional;
}
