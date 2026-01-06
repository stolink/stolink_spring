package com.stolink.backend.domain.project.dto;

import com.stolink.backend.domain.project.entity.WritingGoalType;
import com.stolink.backend.domain.project.entity.WritingGoalUnit;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WritingGoalResponse {
    private WritingGoalType type;
    private Long targetCount;
    private Long currentCount;
    private WritingGoalUnit unit;
    private Boolean isAchieved;
}
