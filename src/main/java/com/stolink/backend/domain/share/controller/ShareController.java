package com.stolink.backend.domain.share.controller;

import com.stolink.backend.domain.project.dto.ProjectResponse;
import com.stolink.backend.domain.share.dto.CreateShareRequest;
import com.stolink.backend.domain.share.dto.ShareResponse;
import com.stolink.backend.domain.share.dto.SharedProjectResponse;
import com.stolink.backend.domain.share.service.ShareService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    // --- Authenticated Endpoints (require X-User-Id) ---

    @GetMapping("/projects/{projectId}/share")
    public ApiResponse<ShareResponse> getShareSettings(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID projectId) {
        ShareResponse response = shareService.getShareSettings(userId, projectId);
        return ApiResponse.ok(response);
    }

    @PostMapping("/projects/{projectId}/share")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShareResponse> createShareLink(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID projectId,
            @RequestBody CreateShareRequest request) {
        ShareResponse response = shareService.createShareLink(userId, projectId, request);
        return ApiResponse.created(response);
    }

    @DeleteMapping("/projects/{projectId}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShareLink(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID projectId) {
        shareService.deleteShareLink(userId, projectId);
    }

    // --- Public Endpoints (No X-User-Id required) ---

    @GetMapping("/share/{shareId}")
    public ApiResponse<SharedProjectResponse> getSharedProject(
            @PathVariable UUID shareId,
            @RequestParam(required = false) String password) {
        SharedProjectResponse response = shareService.getSharedProject(shareId, password);
        return ApiResponse.ok(response);
    }
}
