package com.stolink.backend.domain.ai.service;

import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.ImageCallbackDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * AI Worker 콜백 처리 서비스
 * 
 * Multi-Agent 파이프라인 분석 결과를 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AICallbackService {

    private final RabbitMQProducerService producerService;

    @Value("${app.ai.callback-base-url}")
    private String callbackBaseUrl;

    /**
     * 분석 결과 콜백 처리 (Multi-Agent 파이프라인 결과)
     */
    @Transactional
    public void handleAnalysisCallback(AnalysisCallbackDTO callback) {
        log.info("Processing analysis callback for job: {}, status: {}",
                callback.getJobId(), callback.getStatus());

        if (callback.isFailed()) {
            log.error("Analysis failed for job {}: {}", callback.getJobId(), callback.getError());
            // TODO: Job 상태를 FAILED로 업데이트
            return;
        }

        Map<String, Object> result = callback.getResult();
        if (result == null) {
            log.warn("No result in callback for job: {}", callback.getJobId());
            return;
        }

        // 전체 결과 데이터 로깅 (디버깅용)
        log.debug("Full analysis result: {}", result);

        // 메타데이터 로깅
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
        if (metadata != null) {
            log.info("Analysis metadata - processing_time_ms: {}, tokens_used: {}, trace_id: {}",
                    metadata.get("processing_time_ms"),
                    metadata.get("tokens_used"),
                    metadata.get("trace_id"));
        }

        // 캐릭터 처리
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> characters = (List<Map<String, Object>>) result.get("characters");
        if (characters != null) {
            for (Map<String, Object> character : characters) {
                log.info("Saving character: {}", character.get("name"));
                // TODO: CharacterService를 통해 저장
            }
        }

        // 관계 처리
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relationships = (List<Map<String, Object>>) result.get("relationships");
        if (relationships != null) {
            for (Map<String, Object> relationship : relationships) {
                log.info("Processing relationship: {} -> {}",
                        relationship.get("source_name"), relationship.get("target_name"));
                // TODO: Neo4j CharacterRelationshipService를 통해 저장
            }
        }

        // 이벤트 처리
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) result.get("events");
        if (events != null) {
            for (Map<String, Object> event : events) {
                log.info("Saving event: {}", event.get("summary"));
                // TODO: EventService를 통해 저장
            }
        }

        // 설정(장소) 처리
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> settings = (List<Map<String, Object>>) result.get("settings");
        if (settings != null) {
            for (Map<String, Object> setting : settings) {
                log.info("Processing setting: {}", setting.get("name"));
                // TODO: SettingService를 통해 저장
            }
        }

        // 일관성 보고서 처리
        @SuppressWarnings("unchecked")
        Map<String, Object> consistencyReport = (Map<String, Object>) result.get("consistency_report");
        if (consistencyReport != null) {
            log.info("Consistency report received");
            // TODO: 일관성 이슈 처리 (경고 알림 등)
        }

        log.info("Analysis callback processed successfully for job: {}", callback.getJobId());
        // TODO: Job 상태를 COMPLETED로 업데이트
    }

    /**
     * 이미지 생성 결과 콜백 처리
     */
    @Transactional
    public void handleImageCallback(ImageCallbackDTO callback) {
        log.info("Processing image callback for job: {}, character: {}",
                callback.getJobId(), callback.getCharacterId());

        if ("FAILED".equals(callback.getStatus())) {
            log.error("Image generation failed for character {}: {}",
                    callback.getCharacterId(), callback.getErrorMessage());
            // TODO: 해당 캐릭터의 이미지 상태를 FAILED로 업데이트
            return;
        }

        // 캐릭터 이미지 URL 업데이트
        log.info("Updating character {} with image URL: {}",
                callback.getCharacterId(), callback.getImageUrl());
        // TODO: CharacterService를 통해 imageUrl 필드 업데이트

        log.info("Image callback processed successfully for character: {}", callback.getCharacterId());
    }
}
