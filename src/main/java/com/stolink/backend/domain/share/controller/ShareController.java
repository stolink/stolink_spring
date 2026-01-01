package com.stolink.backend.domain.share.controller;

import com.stolink.backend.domain.share.dto.ShareResponse;
import com.stolink.backend.domain.share.dto.SharedProjectResponse;
import com.stolink.backend.domain.share.service.ShareService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    // --- Authenticated Endpoints (require authentication) ---

    @GetMapping("/projects/{projectId}/share")
    public ApiResponse<ShareResponse> getShareSettings(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {
        ShareResponse response = shareService.getShareSettings(userId, projectId);
        return ApiResponse.ok(response);
    }

    @PostMapping("/projects/{projectId}/share")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShareResponse> createShareLink(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {
        ShareResponse response = shareService.createShareLink(userId, projectId);
        return ApiResponse.created(response);
    }

    @DeleteMapping("/projects/{projectId}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShareLink(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {
        shareService.deleteShareLink(userId, projectId);
    }

    // --- Public Endpoints (No authentication required) ---

    @GetMapping("/share/{shareId}")
    public ApiResponse<SharedProjectResponse> getSharedProject(
            @PathVariable UUID shareId) {
        SharedProjectResponse response = shareService.getSharedProject(shareId);
        return ApiResponse.ok(response);
    }
}
