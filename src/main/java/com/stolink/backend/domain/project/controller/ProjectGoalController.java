package com.stolink.backend.domain.project.controller;

import com.stolink.backend.domain.project.dto.CreateWritingGoalRequest;
import com.stolink.backend.domain.project.dto.WritingGoalResponse;
import com.stolink.backend.domain.project.dto.WritingGoalsResponse;
import com.stolink.backend.domain.project.service.WritingGoalService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectGoalController {

    private final WritingGoalService writingGoalService;

    @GetMapping("/{id}/goals")
    public ApiResponse<WritingGoalsResponse> getGoals(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID projectId) {
        return ApiResponse.ok(writingGoalService.getGoals(userId, projectId));
    }

    @PostMapping("/{id}/goals")
    public ApiResponse<WritingGoalResponse> upsertGoal(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID projectId,
            @RequestBody CreateWritingGoalRequest request) {
        return ApiResponse.ok(writingGoalService.upsertGoal(userId, projectId, request));
    }
}
