package com.stolink.backend.domain.draft.controller;

import com.stolink.backend.domain.draft.dto.DraftCreateRequest;
import com.stolink.backend.domain.draft.dto.DraftDetailResponse;
import com.stolink.backend.domain.draft.dto.DraftResponse;
import com.stolink.backend.domain.draft.service.DraftService;
import com.stolink.backend.domain.draft.service.PublishService;
import com.stolink.backend.global.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;
    private final PublishService publishService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DraftResponse> createDraft(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody DraftCreateRequest request) {
        DraftResponse response = draftService.createDraft(userId, request);
        return ApiResponse.created(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<DraftDetailResponse> getDraft(@PathVariable UUID id) {
        DraftDetailResponse response = draftService.getDraft(id);
        return ApiResponse.ok(response);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Void> publishDraft(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        publishService.publishDraft(userId, id);
        return ApiResponse.ok(null);
    }
}
