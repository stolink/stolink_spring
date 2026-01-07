package com.stolink.backend.domain.project.dto;

import com.stolink.backend.domain.project.entity.WritingGoalType;
import com.stolink.backend.domain.project.entity.WritingGoalUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateWritingGoalRequest {
    private WritingGoalType type;
    private Long targetCount;
    private WritingGoalUnit unit;
}
