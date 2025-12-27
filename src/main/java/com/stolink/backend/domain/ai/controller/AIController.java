package com.stolink.backend.domain.ai.controller;

import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.AnalysisTaskDTO;
import com.stolink.backend.domain.ai.dto.ImageCallbackDTO;
import com.stolink.backend.domain.ai.dto.ImageGenerationRequest;
import com.stolink.backend.domain.ai.dto.ImageGenerationTaskDTO;
import com.stolink.backend.domain.ai.service.AICallbackService;
import com.stolink.backend.domain.ai.service.RabbitMQProducerService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AIController {

    private final RabbitMQProducerService producerService;
    private final AICallbackService callbackService;

    @Value("${app.ai.callback-base-url}")
    private String callbackBaseUrl;

    /**
     * AI 분석 요청
     */
    @PostMapping("/ai/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Map<String, String>> analyze(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody Map<String, Object> request) {
        String jobId = UUID.randomUUID().toString();

        @SuppressWarnings("unchecked")
        AnalysisTaskDTO task = AnalysisTaskDTO.builder()
                .jobId(jobId)
                .projectId(UUID.fromString((String) request.get("projectId")))
                .documentId(UUID.fromString((String) request.get("documentId")))
                .content((String) request.get("content"))
                .callbackUrl(callbackBaseUrl + "/analysis/callback")
                .options((Map<String, Object>) request.get("options"))
                .build();

        producerService.sendAnalysisTask(task);

        return ApiResponse.<Map<String, String>>builder()
                .status(HttpStatus.ACCEPTED)
                .message("Analysis started")
                .data(Map.of(
                        "jobId", jobId,
                        "status", "processing"))
                .build();
    }

    /**
     * Job 상태 조회
     */
    @GetMapping("/ai/jobs/{jobId}")
    public ApiResponse<Map<String, String>> getJobStatus(@PathVariable String jobId) {
        // TODO: Query job status from database
        return ApiResponse.ok(Map.of(
                "jobId", jobId,
                "status", "processing"));
    }

    /**
     * 이미지 생성 요청
     * RabbitMQ에 이미지 생성 작업을 발행합니다.
     */
    @PostMapping("/ai/image/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Map<String, String>> generateImage(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody ImageGenerationRequest request) {
        
        String jobId = UUID.randomUUID().toString();
        
        ImageGenerationTaskDTO task = ImageGenerationTaskDTO.builder()
                .jobId(jobId)
                .projectId(request.getProjectId())
                .characterId(request.getCharacterId())
                .action(request.getAction())
                .message(request.getMessage())
                .imageUrl(request.getImageUrl())
                .editRequest(request.getEditRequest())
                .callbackUrl(callbackBaseUrl + "/image/callback")
                .build();
        
        producerService.sendImageGenerationTask(task);
        
        log.info("Image generation task submitted: jobId={}, action={}, characterId={}",
                jobId, request.getAction(), request.getCharacterId());
        
        return ApiResponse.<Map<String, String>>builder()
                .status(HttpStatus.ACCEPTED)
                .message("Image generation started")
                .data(Map.of(
                        "jobId", jobId,
                        "status", "processing"))
                .build();
    }

    /**
     * Internal callback endpoint for Analysis Worker
     */
    @PostMapping("/internal/ai/analysis/callback")
    public ApiResponse<Void> handleAnalysisCallback(@RequestBody AnalysisCallbackDTO callback) {
        log.info("Received analysis callback for job: {}", callback.getJobId());
        callbackService.handleAnalysisCallback(callback);
        return ApiResponse.ok();
    }

    /**
     * Internal callback endpoint for Image Worker
     */
    @PostMapping("/internal/ai/image/callback")
    public ApiResponse<Void> handleImageCallback(@RequestBody ImageCallbackDTO callback) {
        log.info("Received image callback for job: {}, character: {}",
                callback.getJobId(), callback.getCharacterId());
        callbackService.handleImageCallback(callback);
        return ApiResponse.ok();
    }
}
