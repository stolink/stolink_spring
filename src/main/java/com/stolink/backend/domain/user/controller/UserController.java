package com.stolink.backend.domain.user.controller;

import com.stolink.backend.domain.user.dto.NotificationSettingsResponse;
import com.stolink.backend.domain.user.dto.UpdateNotificationSettingsRequest;
import com.stolink.backend.domain.user.service.UserService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me/notifications")
    public ApiResponse<NotificationSettingsResponse> getNotificationSettings(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(userService.getNotificationSettings(userId));
    }

    @PatchMapping("/me/notifications")
    public ApiResponse<NotificationSettingsResponse> updateNotificationSettings(
            @AuthenticationPrincipal UUID userId,
            @RequestBody UpdateNotificationSettingsRequest request) {
        return ApiResponse.ok(userService.updateNotificationSettings(userId, request));
    }
}
