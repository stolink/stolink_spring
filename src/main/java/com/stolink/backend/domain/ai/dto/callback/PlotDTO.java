package com.stolink.backend.domain.ai.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Plot DTO - AI 분석 결과의 플롯 데이터
 * Python 콜백의 plot 객체에 대응
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlotDTO {

    @JsonProperty("summary")
    private PlotSummaryDTO summary;

    @JsonProperty("plot_summary")
    private PlotSummaryDTO plotSummary;

    private List<ForeshadowingItemDTO> foreshadowing;

    @JsonProperty("narrative_beats")
    private List<Map<String, Object>> narrativeBeats;

    @JsonProperty("tension_curve")
    private List<Map<String, Object>> tensionCurve;

    @JsonProperty("overall_tension")
    private Double overallTension;

    @JsonProperty("three_act_structure")
    private Map<String, Object> threeActStructure;

    @JsonProperty("multimedia_summary")
    private Map<String, Object> multimediaSummary;

    /**
     * PlotSummary nested DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlotSummaryDTO {
        private String narrative;

        @JsonProperty("central_conflict")
        private String centralConflict;
    }

    /**
     * Foreshadowing item DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ForeshadowingItemDTO {
        @JsonProperty("foreshadow_id")
        private String foreshadowId;

        @JsonProperty("hint_text")
        private String hintText;

        @JsonProperty("predicted_outcome")
        private String predictedOutcome;

        private Integer confidence;
    }
}
