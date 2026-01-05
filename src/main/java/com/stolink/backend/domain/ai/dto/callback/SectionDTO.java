package com.stolink.backend.domain.ai.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Section DTO - 문서 분석 결과의 섹션 데이터
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionDTO {

    @JsonProperty("sequence_order")
    private Integer sequenceOrder;

    @JsonProperty("nav_title")
    private String navTitle;

    private String content;

    private List<Double> embedding;

    @JsonProperty("related_characters")
    private List<String> relatedCharacters;

    @JsonProperty("related_events")
    private List<String> relatedEvents;
}
