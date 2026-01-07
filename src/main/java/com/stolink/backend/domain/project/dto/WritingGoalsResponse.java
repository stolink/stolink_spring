package com.stolink.backend.domain.project.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class WritingGoalsResponse {
    private WritingGoalResponse daily;
    private WritingGoalResponse weekly;
    private WritingGoalResponse monthly;
}
