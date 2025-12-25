package com.stolink.backend.domain.ai.service;

import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.ImageCallbackDTO;
import com.stolink.backend.domain.ai.dto.ImageGenerationTaskDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * AI Worker 콜백 처리 서비스
 * 
 * 분석 및 이미지 생성 결과를 처리하고,
 * 필요시 후속 작업(이미지 생성 트리거)을 수행합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AICallbackService {

    private final RabbitMQProducerService producerService;

    @Value("${app.ai.callback-base-url}")
    private String callbackBaseUrl;

    /**
     * 분석 결과 콜백 처리
     * 1. 캐릭터 정보 저장 (PostgreSQL)
     * 2. 관계 그래프 저장 (Neo4j)
     * 3. 캐릭터별 이미지 생성 요청 발송
     */
    @Transactional
    public void handleAnalysisCallback(AnalysisCallbackDTO callback) {
        log.info("Processing analysis callback for job: {}", callback.getJobId());

        if ("FAILED".equals(callback.getStatus())) {
            log.error("Analysis failed for job {}: {}", callback.getJobId(), callback.getErrorMessage());
            // TODO: Job 상태를 FAILED로 업데이트
            return;
        }

        // 1. 캐릭터 속성 저장 (PostgreSQL)
        if (callback.getCharacters() != null) {
            for (AnalysisCallbackDTO.CharacterInfo character : callback.getCharacters()) {
                log.info("Saving character: {}", character.getName());
                // TODO: CharacterService를 통해 저장

                // 3. 각 캐릭터에 대해 이미지 생성 요청
                triggerImageGeneration(
                        callback.getJobId(),
                        callback.getProjectId(),
                        character);
            }
        }

        // 2. 관계 그래프 저장 (Neo4j)
        if (callback.getRelationships() != null) {
            for (AnalysisCallbackDTO.RelationshipInfo relationship : callback.getRelationships()) {
                log.info("Saving relationship: {} -> {} ({})",
                        relationship.getSourceCharacter(),
                        relationship.getTargetCharacter(),
                        relationship.getRelationshipType());
                // TODO: Neo4j CharacterRelationshipService를 통해 저장
            }
        }

        // 복선 정보 저장
        if (callback.getForeshadowings() != null) {
            for (AnalysisCallbackDTO.ForeshadowingInfo foreshadowing : callback.getForeshadowings()) {
                log.info("Saving foreshadowing: {}", foreshadowing.getTitle());
                // TODO: ForeshadowingService를 통해 저장
            }
        }

        log.info("Analysis callback processed successfully for job: {}", callback.getJobId());
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

        // TODO: 모든 이미지 생성이 완료되었는지 확인 후 Job 상태를 COMPLETED로 변경
        log.info("Image callback processed successfully for character: {}", callback.getCharacterId());
    }

    /**
     * 캐릭터 이미지 생성 요청 발송
     */
    private void triggerImageGeneration(String jobId, UUID projectId,
            AnalysisCallbackDTO.CharacterInfo character) {
        ImageGenerationTaskDTO imageTask = ImageGenerationTaskDTO.builder()
                .jobId(jobId + "-img-" + UUID.randomUUID().toString().substring(0, 8))
                .projectId(projectId)
                .characterId(UUID.randomUUID()) // TODO: 실제 저장된 캐릭터 ID 사용
                .characterName(character.getName())
                .description(character.getDescription())
                .style("anime")
                .callbackUrl(callbackBaseUrl + "/image/callback")
                .build();

        producerService.sendImageGenerationTask(imageTask);
        log.info("Image generation task sent for character: {}", character.getName());
    }
}
