package com.stolink.backend.domain.setting.controller;

import com.stolink.backend.domain.setting.dto.SettingResponse;
import com.stolink.backend.domain.setting.service.SettingService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    @GetMapping
    public ApiResponse<List<SettingResponse>> getSettings(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {
        try {
            List<SettingResponse> settings = settingService.getSettingsByProject(userId, projectId);
            return ApiResponse.ok(settings);
        } catch (Exception e) {
            e.printStackTrace(); // 로컬 콘솔에 스택 트레이스 출력
            throw e; // 다시 던져서 500 응답 유지
        }
    }
}
