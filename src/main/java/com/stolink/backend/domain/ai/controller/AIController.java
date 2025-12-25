package com.stolink.backend.domain.ai.controller;

import com.stolink.backend.domain.ai.dto.AnalysisTaskDTO;
import com.stolink.backend.domain.ai.service.RabbitMQProducerService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                .callbackUrl("http://localhost:8080/api/internal/ai/callback")
                .options((Map<String, Object>) request.get("options"))
                .build();

        producerService.sendAnalysisTask(task);

        return ApiResponse.success(Map.of(
                "jobId", jobId,
                "status", "processing"));
    }

    @GetMapping("/ai/jobs/{jobId}")
    public ApiResponse<Map<String, String>> getJobStatus(@PathVariable String jobId) {
        // In a real implementation, query job status from database
        return ApiResponse.success(Map.of(
                "jobId", jobId,
                "status", "processing"));
    }

    /**
     * Internal callback endpoint for AI workers
     */
    @PostMapping("/internal/ai/callback")
    public ApiResponse<Void> handleCallback(@RequestBody Map<String, Object> result) {
        log.info("Received AI callback: {}", result);

        // TODO: Process analysis result
        // 1. Save to PostgreSQL (character attributes, foreshadowing)
        // 2. Save to Neo4j (character relationships)
        // 3. Trigger image generation task if needed

        return ApiResponse.success();
    }
}
