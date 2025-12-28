package com.stolink.backend.domain.ai.dto.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * 기존 관계 참조 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExistingRelationshipRef {

    @JsonProperty("source_name")
    private String sourceName;

    @JsonProperty("target_name")
    private String targetName;

    @JsonProperty("relation_type")
    private String relationType;

    private Integer strength;
}
