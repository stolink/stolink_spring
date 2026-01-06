package com.stolink.backend.domain.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.ImageCallbackDTO;
import com.stolink.backend.domain.ai.dto.callback.*;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.repository.CharacterRepository;
import com.stolink.backend.domain.character.entity.ImageGenerationTask;
import com.stolink.backend.domain.character.repository.ImageGenerationTaskRepository;

import com.stolink.backend.domain.event.node.Event;
import com.stolink.backend.domain.event.repository.EventNeo4jRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.setting.node.Setting;
import com.stolink.backend.domain.setting.repository.SettingNeo4jRepository;
import com.stolink.backend.domain.plot.entity.PlotIntegration;
import com.stolink.backend.domain.plot.repository.PlotIntegrationRepository;
import com.stolink.backend.domain.consistency.entity.ConsistencyReport;
import com.stolink.backend.domain.consistency.repository.ConsistencyReportRepository;
import com.stolink.backend.domain.validation.entity.ValidationResult;
import com.stolink.backend.domain.validation.repository.ValidationResultRepository;
import com.stolink.backend.domain.foreshadowing.entity.Foreshadowing;
import com.stolink.backend.domain.foreshadowing.repository.ForeshadowingRepository;
import com.stolink.backend.global.sse.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * AI Worker 콜백 처리 서비스
 *
 * Multi-Agent 파이프라인 분석 결과를 처리하고 DB에 저장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AICallbackService {

    private final CharacterRepository characterRepository;
    private final com.stolink.backend.domain.character.repository.CharacterJpaRepository characterJpaRepository;
    private final EventNeo4jRepository eventNeo4jRepository;

    private final SettingNeo4jRepository settingNeo4jRepository;
    private final ImageGenerationTaskRepository imageGenerationTaskRepository;

    private final AnalysisJobRepository analysisJobRepository;
    private final PlotIntegrationRepository plotIntegrationRepository;
    private final ConsistencyReportRepository consistencyReportRepository;
    private final ValidationResultRepository validationResultRepository;
    private final ForeshadowingRepository foreshadowingRepository;
    private final ObjectMapper objectMapper;
    private final SseEmitterService sseEmitterService;

    @Value("${app.ai.callback-base-url}")
    private String callbackBaseUrl;

    /**
     * 분석 결과 콜백 처리 (Multi-Agent 파이프라인 결과)
     *
     * Python AI Agent 콜백 구조:
     * {
     *   "jobId": "...",
     *   "status": "COMPLETED",
     *   "result": {
     *     "characters": [...],
     *     "events": [...],
     *     "settings": [...],
     *     "relationships": [{ "source": "Name A", "target": "Name B", "relation_type": "FRIEND", ... }],
     *     "plot": { "summary": "...", "foreshadowing": [...] },
     *     "consistency_report": { "score": 95, "conflicts": [...] },
     *     "validation": { "is_valid": true, "quality_score": 98 },
     *     "metadata": { "processing_time_ms": 1234 }
     *   }
     * }
     */
    @Transactional
    public void handleAnalysisCallback(AnalysisCallbackDTO callback) {
        log.info("Processing analysis callback for job: {}, status: {}",
                callback.getJobId(), callback.getStatus());

        // Job 조회
        AnalysisJob job = analysisJobRepository.findByJobId(callback.getJobId()).orElse(null);
        if (job == null) {
            log.error("Job not found: {}", callback.getJobId());
            return;
        }

        // 실패 처리
        if (callback.isFailed()) {
            log.error("Analysis failed for job {}: {}", callback.getJobId(), callback.getError());
            job.markAsFailed(callback.getError());
            analysisJobRepository.save(job);
            return;
        }

        // Job에서 Project와 projectId 획득
        Project project = job.getProject();
        String projectIdStr = project.getId().toString();

        // 1. 캐릭터 저장 (Neo4j & Postgres) - using effective getter
        saveCharacters(callback.getEffectiveCharacters(), project);

        // 2. 관계 저장 (Neo4j) - using effective getter
        // Relationships reference characters by NAME, not ID
        saveRelationships(callback.getEffectiveRelationships(), projectIdStr);

        // 3. 감정 정보를 캐릭터에 업데이트 (Neo4j)
        Map<String, Object> emotions = callback.getEffectiveEmotions();
        if (emotions != null) {
            updateEmotions(emotions, projectIdStr);
        }

        // 4. 이벤트 저장 (Neo4j) - using effective getter
        saveEvents(callback.getEffectiveEvents(), project);

        // 5. 설정(장소) 저장 (Neo4j) - using effective getter
        saveSettings(callback.getEffectiveSettings(), project);

        // 6. 플롯 저장 (PostgreSQL) - using effective getter
        PlotDTO plotData = callback.getEffectivePlot();
        if (plotData != null) {
            savePlotIntegration(plotData, project, job.getDocumentId());
            // 복선도 plot에서 추출
            saveForeshadowing(plotData, project);
        }

        // 7. 일관성 보고서 저장 (PostgreSQL) - using effective getter
        ConsistencyReportDTO consistencyData = callback.getEffectiveConsistencyReport();
        if (consistencyData != null) {
            logConsistencyReport(consistencyData);
            saveConsistencyReport(consistencyData, project, callback.getJobId());
        }

        // 8. 검증 결과 저장 (PostgreSQL) - using effective getter
        ValidationDTO validationData = callback.getEffectiveValidation();
        if (validationData != null) {
            saveValidationResult(validationData, project, callback.getJobId());
        }

        // Job 완료 처리 - using effective getter
        Long processingTimeMs = callback.getEffectiveProcessingTimeMs();
        job.markAsCompleted(processingTimeMs);
        analysisJobRepository.save(job);

        // SSE 알림 전송 (프론트엔드 업데이트용)
        sseEmitterService.sendStatus(job.getProject().getId(), new SseEmitterService.AnalysisStatusEvent(
                "COMPLETED", 1, 1, "분석이 완료되었습니다."));

        log.info("Analysis callback processed successfully for job: {}", callback.getJobId());
    }

    // No helper methods for metadata extraction needed anymore, direct access from
    // DTO

    /**
     * 캐릭터 저장 (Neo4j)
     */
    private void saveCharacters(List<CharacterDTO> characters, Project project) {
        String projectId = project.getId().toString();
        if (characters == null || characters.isEmpty()) {
            log.info("No characters to save");
            return;
        }

        for (CharacterDTO charData : characters) {
            String name = null;
            if (charData.getProfile() != null) {
                name = charData.getProfile().getName();
            }

            if (name == null || name.isBlank()) {
                log.warn("Skipping character with empty name");
                continue;
            }

            String role = charData.getRole();
            String status = charData.getStatus();

            Optional<Character> existingChar = characterRepository.findByNameAndProjectId(name, projectId);

            if (existingChar.isPresent()) {
                Character character = existingChar.get();
                character.setRole(role);
                character.setStatus(status);
                updateCharacterJsonFields(character, charData);
                characterRepository.save(character);
                log.info("Updated character: {} (id: {})", name, character.getId());
            } else {
                Character character = Character.builder()
                        .projectId(projectId)
                        .name(name)
                        .role(role)
                        .status(status)
                        .build();
                updateCharacterJsonFields(character, charData);
                character = characterRepository.save(character);
                log.info("Created character: {} (id: {})", name, character.getId());
            }

            // PostgreSQL 저장 (AI 서버 호환용)
            try {
                saveCharacterToPostgres(charData, project);
            } catch (Exception e) {
                log.error("Failed to save character to Postgres: {}", e.getMessage());
            }
        }
    }

    private void saveCharacterToPostgres(CharacterDTO charData, Project project) {
        String name = null;
        if (charData.getProfile() != null) {
            name = charData.getProfile().getName();
        }

        if (name == null) {
            log.error("Cannot save character to Postgres: Name is missing");
            return;
        }
        com.stolink.backend.domain.character.entity.CharacterEntity entity = characterJpaRepository
                .findByProjectAndName(project, name)
                .orElse(com.stolink.backend.domain.character.entity.CharacterEntity.builder()
                        .project(project)
                        .name(name)
                        .build());

        // Basic fields
        entity.setCharacterId(charData.getId());
        entity.setRole(charData.getRole());
        entity.setStatus(charData.getStatus());

        // Profile fields
        if (charData.getProfile() != null) {
            CharacterDTO.ProfileDTO profile = charData.getProfile();
            entity.setAge(profile.getAge());
            entity.setGender(profile.getGender());
            entity.setRace(profile.getRace());
            entity.setMbti(profile.getMbti());
            entity.setBackstory(profile.getBackstory());
            if (profile.getFaction() != null) {
                entity.setFaction(profile.getFaction().getName());
            }
        }

        try {
            // Aliases
            if (charData.getAliases() != null)
                entity.setAliasesJson(objectMapper.writeValueAsString(charData.getAliases()));

            // Profile full JSON
            if (charData.getProfile() != null)
                entity.setProfileJson(objectMapper.writeValueAsString(charData.getProfile()));

            // Appearance
            if (charData.getAppearance() != null) {
                String appearanceJson = objectMapper.writeValueAsString(charData.getAppearance());
                entity.setAppearanceJson(appearanceJson);
                // Visual (legacy) fallback
                entity.setVisualJson(appearanceJson);
            }

            // Personality
            if (charData.getProfile() != null && charData.getProfile().getPersonality() != null) {
                entity.setPersonalityJson(objectMapper.writeValueAsString(charData.getProfile().getPersonality()));
            }

            // Relations
            if (charData.getRelations() != null)
                entity.setRelationsJson(objectMapper.writeValueAsString(charData.getRelations()));

            // Current Mood
            if (charData.getCurrentMood() != null)
                entity.setCurrentMoodJson(objectMapper.writeValueAsString(charData.getCurrentMood()));

            // Meta
            if (charData.getMeta() != null)
                entity.setMetaJson(objectMapper.writeValueAsString(charData.getMeta()));

            // Embedding
            if (charData.getEmbedding() != null)
                entity.setEmbeddingJson(objectMapper.writeValueAsString(charData.getEmbedding()));

        } catch (JsonProcessingException e) {
            log.error("JSON processing error for character entity: {}", e.getMessage());
        }

        // Motivation and first appearance - NOT in new DTO structure explicitly?
        // Checking CharacterDTO: No motivation field.
        // It might be inside 'meta' or 'profile' in some versions but my DTO doesn't
        // have it.
        // I will omit them if not present in DTO.

        characterJpaRepository.save(entity);
        log.info("Saved character to Postgres: {}", name);
    }

    /**
     * 캐릭터 JSON 필드 업데이트 (Neo4j)
     */
    private void updateCharacterJsonFields(Character character, CharacterDTO charData) {
        try {
            // AI generated ID
            character.setCharacterId(charData.getId());

            // Profile fields
            if (charData.getProfile() != null) {
                CharacterDTO.ProfileDTO profile = charData.getProfile();
                character.setAge(profile.getAge());
                character.setGender(profile.getGender());
                character.setRace(profile.getRace());
                character.setMbti(profile.getMbti());
                character.setBackstory(profile.getBackstory());
                if (profile.getFaction() != null) {
                    character.setFaction(profile.getFaction().getName());
                }
                character.setProfileJson(objectMapper.writeValueAsString(profile));
            }

            // Aliases
            if (charData.getAliases() != null) {
                character.setAliasesJson(objectMapper.writeValueAsString(charData.getAliases()));
            }

            // Appearance
            if (charData.getAppearance() != null) {
                String appearanceJson = objectMapper.writeValueAsString(charData.getAppearance());
                character.setAppearanceJson(appearanceJson);
                // Visual (legacy)
                character.setVisualJson(appearanceJson);
            }

            // Personality
            if (charData.getProfile() != null && charData.getProfile().getPersonality() != null) {
                character.setPersonalityJson(objectMapper.writeValueAsString(charData.getProfile().getPersonality()));
            }

            // Relations
            if (charData.getRelations() != null) {
                character.setRelationsJson(objectMapper.writeValueAsString(charData.getRelations()));
            }

            // Current Mood
            if (charData.getCurrentMood() != null) {
                character.setCurrentMoodJson(objectMapper.writeValueAsString(charData.getCurrentMood()));
            }

            // Meta
            if (charData.getMeta() != null) {
                character.setMetaJson(objectMapper.writeValueAsString(charData.getMeta()));
            }

            // Embedding
            if (charData.getEmbedding() != null) {
                character.setEmbeddingJson(objectMapper.writeValueAsString(charData.getEmbedding()));
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize character data to JSON: {}", e.getMessage());
        }
    }

    /**
     * 관계 저장 (Neo4j)
     *
     * Python 콜백의 relationships 구조:
     * [{ "source": "Name A", "target": "Name B", "relation_type": "FRIEND", "strength": 8, "description": "..." }]
     *
     * source/target are CHARACTER NAMES, not IDs.
     * Creates placeholder characters if they don't exist to ensure no relationship data is lost.
     */
    private void saveRelationships(List<RelationshipDTO> relationships, String projectId) {
        log.info("Saving {} relationships for project: {}",
            relationships != null ? relationships.size() : 0, projectId);

        if (relationships == null || relationships.isEmpty()) {
            log.info("No relationships to save");
            return;
        }

        for (RelationshipDTO relData : relationships) {
            String sourceName = relData.getSource();
            String targetName = relData.getTarget();
            String relationType = relData.getRelationType();
            Integer strength = relData.getStrength() != null ? relData.getStrength() : 5;
            String description = relData.getDescription();

            if (sourceName == null || targetName == null) {
                log.warn("Skipping relationship with missing source/target: {} -> {}", sourceName, targetName);
                continue;
            }

            // Find or create source character
            Character sourceChar = characterRepository.findByNameAndProjectId(sourceName, projectId)
                    .orElseGet(() -> {
                        log.info("Creating placeholder character for source: {} in project: {}", sourceName, projectId);
                        Character placeholder = Character.builder()
                                .projectId(projectId)
                                .name(sourceName)
                                .role("unknown")
                                .status("unknown")
                                .build();
                        return characterRepository.save(placeholder);
                    });

            // Find or create target character
            Character targetChar = characterRepository.findByNameAndProjectId(targetName, projectId)
                    .orElseGet(() -> {
                        log.info("Creating placeholder character for target: {} in project: {}", targetName, projectId);
                        Character placeholder = Character.builder()
                                .projectId(projectId)
                                .name(targetName)
                                .role("unknown")
                                .status("unknown")
                                .build();
                        return characterRepository.save(placeholder);
                    });

            try {
                characterRepository.createRelationship(
                        sourceChar.getId(),
                        targetChar.getId(),
                        relationType != null ? relationType.toLowerCase() : "related",
                        strength,
                        description);
                log.info("Created relationship in Neo4j: {} -[{}]-> {}", sourceName, relationType, targetName);
            } catch (Exception e) {
                log.error("Failed to create relationship in Neo4j: {} -> {}: {}", sourceName, targetName, e.getMessage());
            }
        }
    }

    /**
     * 이벤트 저장 (Neo4j)
     */
    private void saveEvents(List<EventDTO> events, Project project) {
        if (events == null || events.isEmpty()) {
            log.info("No events to save");
            return;
        }

        String projectId = project.getId().toString();

        for (EventDTO eventData : events) {
            String eventId = eventData.getEventId();
            String eventType = eventData.getEventType();
            String narrativeSummary = eventData.getNarrativeSummary();
            String description = eventData.getDescription();
            String visualScene = eventData.getVisualScene();
            String cameraAngle = eventData.getCameraAngle();
            String locationRef = eventData.getLocationRef();
            String prevEventId = eventData.getPrevEventId();
            Integer importance = eventData.getImportance() != null ? eventData.getImportance() : 5;
            Boolean isForeshadowing = eventData.getIsForeshadowing();

            // participants를 JSON 문자열로
            String participantsJson = null;
            if (eventData.getParticipants() != null) {
                try {
                    participantsJson = objectMapper.writeValueAsString(eventData.getParticipants());
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize participants: {}", e.getMessage());
                }
            }

            // 기존 이벤트 조회 또는 새로 생성
            Optional<Event> existingEvent = eventNeo4jRepository.findByProjectIdAndEventId(projectId, eventId);

            Event event;
            if (existingEvent.isPresent()) {
                event = existingEvent.get();
            } else {
                event = Event.builder()
                        .projectId(projectId)
                        .eventId(eventId)
                        .build();
            }

            event.setEventType(eventType != null ? eventType.toUpperCase() : null);
            event.setNarrativeSummary(narrativeSummary);
            event.setDescription(description);
            event.setVisualScene(visualScene);
            event.setCameraAngle(cameraAngle);
            event.setLocationRef(locationRef);
            event.setPrevEventId(prevEventId);
            event.setImportance(importance);
            event.setIsForeshadowing(isForeshadowing != null ? isForeshadowing : false);
            event.setParticipantsJson(participantsJson);

            // New fields from callback_result.json
            event.setChapter(eventData.getChapter());
            event.setSequenceOrder(eventData.getSequenceOrder());
            event.setDocumentId(eventData.getDocumentId());

            // New AI schema fields
            try {
                if (eventData.getTimestamp() != null) {
                    event.setTimestampJson(objectMapper.writeValueAsString(eventData.getTimestamp()));
                }
                if (eventData.getChangesMade() != null) {
                    event.setChangesJson(objectMapper.writeValueAsString(eventData.getChangesMade()));
                }
                if (eventData.getEmbedding() != null) {
                    event.setEmbeddingJson(objectMapper.writeValueAsString(eventData.getEmbedding()));
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize event JSON fields: {}", e.getMessage());
            }

            eventNeo4jRepository.save(event);
            log.info("Saved event to Neo4j: {} (chapter: {}, seq: {})", eventId, eventData.getChapter(),
                    eventData.getSequenceOrder());
        }
    }

    /**
     * 설정(장소) 저장 (Neo4j)
     */
    private void saveSettings(List<SettingDTO> settings, Project project) {
        if (settings == null || settings.isEmpty()) {
            log.info("No settings to save");
            return;
        }

        String projectId = project.getId().toString();

        for (SettingDTO settingData : settings) {
            String settingId = settingData.getSettingId();
            String name = settingData.getName();
            String locationType = settingData.getLocationType();
            String visualPrompt = settingData.getVisualBackground(); // DTO doesn't have static_visual_prompt, check
                                                                     // JSON mapping
            // Wait, JSON has `static_visual_prompt`? callback_result JSON analysis showed
            // `visual_background`.
            // My DTO implementation `SettingDTO` mapped `visual_background`.

            String timeOfDay = settingData.getTimeOfDay();
            String lightingDescription = settingData.getLighting();
            String atmosphereKeywords = settingData.getAtmosphere();
            String weatherCondition = settingData.getWeather();

            Boolean isPrimary = settingData.getIsPrimary();
            String storySignificance = settingData.getSignificance();

            // static_objects를 JSON 문자열로
            String staticObjectsJson = null;
            if (settingData.getNotableFeatures() != null) {
                try {
                    staticObjectsJson = objectMapper.writeValueAsString(settingData.getNotableFeatures());
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize static_objects: {}", e.getMessage());
                }
            }

            // 기존 설정 조회 또는 새로 생성
            Optional<Setting> existingSetting = settingNeo4jRepository.findByProjectIdAndName(projectId, name);

            Setting setting;
            if (existingSetting.isPresent()) {
                setting = existingSetting.get();
            } else {
                setting = Setting.builder()
                        .projectId(projectId)
                        .settingId(settingId)
                        .name(name)
                        .build();
            }

            setting.setLocationType(locationType != null ? locationType.toUpperCase() : null);
            setting.setLocationName(settingData.getLocationName());
            setting.setVisualPrompt(visualPrompt);
            setting.setVisualBackground(settingData.getVisualBackground());
            setting.setTimeOfDay(timeOfDay);
            setting.setLightingDescription(lightingDescription);
            setting.setAtmosphereKeywords(atmosphereKeywords);
            setting.setWeatherCondition(weatherCondition);
            setting.setArtStyle(settingData.getArtStyle());
            setting.setDescription(settingData.getDescription());
            setting.setIsPrimaryLocation(isPrimary != null ? isPrimary : false);
            setting.setStorySignificance(storySignificance);
            setting.setStaticObjectsJson(staticObjectsJson);

            // New fields from callback_result.json
            setting.setParentLocation(settingData.getParentLocation());
            setting.setFirstMentioned(settingData.getFirstMentioned());

            // Embedding JSON
            try {
                if (settingData.getEmbedding() != null) {
                    setting.setEmbeddingJson(objectMapper.writeValueAsString(settingData.getEmbedding()));
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize setting embedding: {}", e.getMessage());
            }

            settingNeo4jRepository.save(setting);
            log.info("Saved setting to Neo4j: {} (type: {}, primary: {})", name, locationType, isPrimary);
        }
    }

    /**
     * 감정 정보를 캐릭터에 업데이트 (Neo4j)
     */
    @SuppressWarnings("unchecked")
    private void updateEmotions(Map<String, Object> result, String projectId) {
        Map<String, Object> emotionsData = (Map<String, Object>) result.get("emotions");
        if (emotionsData == null) {
            log.info("No emotions to update");
            return;
        }

        List<Map<String, Object>> neo4jUpdates = (List<Map<String, Object>>) emotionsData.get("neo4j_updates");
        if (neo4jUpdates == null || neo4jUpdates.isEmpty()) {
            log.info("No neo4j emotion updates");
            return;
        }

        for (Map<String, Object> update : neo4jUpdates) {
            String characterName = (String) update.get("character_name");
            Map<String, Object> propertyUpdates = (Map<String, Object>) update.get("property_updates");

            if (characterName == null || propertyUpdates == null) {
                continue;
            }

            Optional<Character> charOpt = characterRepository.findByNameAndProjectId(characterName, projectId);
            if (charOpt.isPresent()) {
                Character character = charOpt.get();
                try {
                    character.setCurrentMoodJson(objectMapper.writeValueAsString(propertyUpdates));
                    characterRepository.save(character);
                    log.info("Updated emotions for character: {}", characterName);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize emotion data: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 일관성 보고서 로깅
     */
    private void logConsistencyReport(ConsistencyReportDTO consistencyReport) {
        if (consistencyReport != null) {
            Integer score = consistencyReport.getEffectiveScore();
            Boolean requiresReextraction = consistencyReport.getRequiresReextraction();
            int conflictCount = consistencyReport.getConflicts() != null ? consistencyReport.getConflicts().size() : 0;
            int warningCount = consistencyReport.getWarnings() != null ? consistencyReport.getWarnings().size() : 0;

            log.info("Consistency report - score: {}, requires_reextraction: {}, conflicts: {}, warnings: {}",
                    score, requiresReextraction, conflictCount, warningCount);
        }
    }

    /**
     * 이미지 생성 결과 콜백 처리
     */
    @Transactional
    public void handleImageCallback(ImageCallbackDTO callback) {
        log.info("Processing image callback for job: {}, character: {}",
                callback.getJobId(), callback.getCharacterId());

        String jobId = callback.getJobId();
        String characterId = callback.getCharacterId().toString();

        // URL 수정 (minio -> localhost) - 로컬 환경 호환성
        String imageUrl = callback.getImageUrl();
        if (imageUrl != null && imageUrl.contains("minio:9000")) {
            imageUrl = imageUrl.replace("minio:9000", "localhost:8080/media");
        }

        if ("FAILED".equals(callback.getStatus())) {
            log.error("Image generation failed for character {}: {}",
                    characterId, callback.getErrorMessage());

            // Task 실패 상태 업데이트
            final String errorMsg = callback.getErrorMessage();
            imageGenerationTaskRepository.findById(jobId).ifPresent(task -> {
                task.markAsFailed(errorMsg);
                imageGenerationTaskRepository.save(task);
            });
            return;
        }

        // 1. Character 노드 업데이트
        final String finalImageUrl = imageUrl;
        characterRepository.findById(characterId).ifPresent(character -> {
            character.setImageUrl(finalImageUrl);
            characterRepository.save(character);
            log.info("Updated character {} with image URL: {}", characterId, finalImageUrl);
        });

        // 2. ImageGenerationTask 상태 업데이트 (COMPLETED)
        imageGenerationTaskRepository.findById(jobId).ifPresent(task -> {
            task.setImageUrl(finalImageUrl);
            task.setStatus(ImageGenerationTask.TaskStatus.COMPLETED);
            imageGenerationTaskRepository.save(task);
            log.info("Updated ImageGenerationTask {} to COMPLETED", jobId);
        });
    }

    /**
     * 플롯 통합 저장 (PostgreSQL)
     *
     * Python 콜백의 "plot" 필드 구조:
     * {
     *   "summary": "...",
     *   "plot_summary": { "narrative": "...", "central_conflict": "..." },
     *   "foreshadowing": [{ "foreshadow_id": "...", "hint_text": "...", ... }],
     *   "narrative_beats": [...],
     *   "tension_curve": [...],
     *   "overall_tension": 0.75,
     *   "three_act_structure": {...},
     *   "multimedia_summary": {...}
     * }
     */
    private void savePlotIntegration(PlotDTO plotData, Project project, UUID documentId) {
        if (plotData == null) {
            log.info("No plot to save");
            return;
        }

        try {
            // Extract narrative and central conflict
            String narrative = null;
            String centralConflict = null;

            // Try plot_summary first
            if (plotData.getPlotSummary() != null) {
                narrative = plotData.getPlotSummary().getNarrative();
                centralConflict = plotData.getPlotSummary().getCentralConflict();
            }
            // Fall back to summary field (which can now be an object)
            if (narrative == null && plotData.getSummary() != null) {
                narrative = plotData.getSummary().getNarrative();
                if (centralConflict == null) {
                    centralConflict = plotData.getSummary().getCentralConflict();
                }
            }

            PlotIntegration plot = PlotIntegration.builder()
                    .project(project)
                    .documentId(documentId)
                    .narrative(narrative)
                    .centralConflict(centralConflict)
                    .overallTension(plotData.getOverallTension())
                    .narrativeBeatsJson(toJson(plotData.getNarrativeBeats()))
                    .tensionCurveJson(toJson(plotData.getTensionCurve()))
                    .threeActStructureJson(toJson(plotData.getThreeActStructure()))
                    .foreshadowingJson(toJson(plotData.getForeshadowing()))
                    .multimediaSummaryJson(toJson(plotData.getMultimediaSummary()))
                    .build();

            plotIntegrationRepository.save(plot);
            log.info("Saved plot integration for project: {}", project.getId());
        } catch (Exception e) {
            log.error("Failed to save plot integration: {}", e.getMessage());
        }
    }

    /**
     * 일관성 보고서 저장 (PostgreSQL)
     *
     * Python 콜백의 "consistency_report" 필드 구조:
     * {
     *   "score": 95,  // or "overall_score"
     *   "conflicts": [{ "type": "...", "description": "...", ... }],
     *   "warnings": ["..."],
     *   "requires_reextraction": false,
     *   "resolution_summary": {...},
     *   "neo4j_validation": {...}
     * }
     */
    private void saveConsistencyReport(ConsistencyReportDTO reportData, Project project, String jobId) {
        if (reportData == null) {
            log.info("No consistency_report to save");
            return;
        }

        try {
            // Use effective score (prefers 'score' over 'overall_score')
            Integer score = reportData.getEffectiveScore();

            ConsistencyReport report = ConsistencyReport.builder()
                    .project(project)
                    .jobId(jobId)
                    .overallScore(score)
                    .requiresReextraction(reportData.getRequiresReextraction() != null
                            ? reportData.getRequiresReextraction() : false)
                    .conflictsJson(toJson(reportData.getConflicts()))
                    .warningsJson(toJson(reportData.getWarnings()))
                    .resolutionSummaryJson(toJson(reportData.getResolutionSummary()))
                    .neo4jValidationJson(toJson(reportData.getNeo4jValidation()))
                    .build();

            consistencyReportRepository.save(report);
            log.info("Saved consistency report for job: {}, score: {}", jobId, score);
        } catch (Exception e) {
            log.error("Failed to save consistency report: {}", e.getMessage());
        }
    }

    /**
     * 검증 결과 저장 (PostgreSQL)
     */
    @SuppressWarnings("unchecked")
    private void saveValidationResult(ValidationDTO validationData, Project project, String jobId) {
        if (validationData == null) {
            log.info("No validation to save");
            return;
        }

        try {

            ValidationResult validation = ValidationResult.builder()
                    .project(project)
                    .jobId(jobId)
                    .isValid(validationData.getIsValid() != null ? validationData.getIsValid() : true)
                    .qualityScore(validationData.getQualityScore())
                    .action(validationData.getAction())
                    .actionDescription(validationData.getActionDescription())
                    .averageCompleteness(validationData.getAverageCompleteness())
                    .errorCount(validationData.getErrorCount() != null ? validationData.getErrorCount() : 0)
                    .warningCount(validationData.getWarningCount() != null ? validationData.getWarningCount() : 0)
                    .executionTimeMs(validationData.getExecutionTimeMs())
                    .dataCompletenessJson(toJson(validationData.getDataCompleteness()))
                    .validationDetailsJson(toJson(validationData.getValidationDetails()))
                    .build();

            validationResultRepository.save(validation);
            log.info("Saved validation result for job: {}, quality_score: {}", jobId, validationData.getQualityScore());
        } catch (Exception e) {
            log.error("Failed to save validation result: {}", e.getMessage());
        }
    }

    /**
     * 복선 저장 (PostgreSQL) - plot.foreshadowing에서 추출
     *
     * Python 콜백의 foreshadowing 구조:
     * [
     *   { "foreshadow_id": "...", "hint_text": "...", "predicted_outcome": "...", "confidence": 85 }
     * ]
     */
    private void saveForeshadowing(PlotDTO plotData, Project project) {
        if (plotData == null || plotData.getForeshadowing() == null || plotData.getForeshadowing().isEmpty()) {
            log.info("No foreshadowing to save");
            return;
        }

        for (PlotDTO.ForeshadowingItemDTO fsData : plotData.getForeshadowing()) {
            String foreshadowId = fsData.getForeshadowId();
            String hintText = fsData.getHintText();
            String predictedOutcome = fsData.getPredictedOutcome();
            Integer confidence = fsData.getConfidence();

            if (foreshadowId == null || foreshadowId.isBlank()) {
                continue;
            }

            // 기존 복선 조회 또는 새로 생성
            Optional<Foreshadowing> existingFs = foreshadowingRepository.findByProjectAndTag(project, foreshadowId);

            Foreshadowing foreshadowing;
            if (existingFs.isPresent()) {
                foreshadowing = existingFs.get();
                foreshadowing.update(hintText,
                        confidence != null && confidence >= 7 ? Foreshadowing.Importance.MAJOR
                                : Foreshadowing.Importance.MINOR);
            } else {
                foreshadowing = Foreshadowing.builder()
                        .project(project)
                        .tag(foreshadowId)
                        .description(hintText + (predictedOutcome != null ? " -> " + predictedOutcome : ""))
                        .importance(confidence != null && confidence >= 7 ? Foreshadowing.Importance.MAJOR
                                : Foreshadowing.Importance.MINOR)
                        .build();
            }

            foreshadowingRepository.save(foreshadowing);
            log.info("Saved foreshadowing: {} (confidence: {})", foreshadowId, confidence);
        }
    }

    /**
     * Object를 JSON 문자열로 변환
     */
    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize to JSON: {}", e.getMessage());
            return null;
        }
    }

    // ============================================================
    // 대용량 문서 분석 아키텍처 (Document Analysis Architecture)
    // ============================================================

    private final com.stolink.backend.domain.document.repository.DocumentRepository documentRepository;
    private final com.stolink.backend.domain.document.repository.SectionRepository sectionRepository;
    private final DocumentAnalysisPublisher documentAnalysisPublisher;

    /**
     * 문서 분석 결과 콜백 처리 (1차 Pass)
     *
     * 각 Document(TEXT)의 분석 결과를 처리하고 Section을 저장합니다.
     * 모든 문서 분석 완료 시 2차 Pass(글로벌 병합)를 트리거합니다.
     */
    @Transactional
    public void handleDocumentAnalysisCallback(com.stolink.backend.domain.ai.dto.DocumentAnalysisCallbackDTO callback) {
        log.info("Processing document analysis callback for document: {}, status: {}",
                callback.getDocumentId(), callback.getStatus());

        UUID documentId = UUID.fromString(callback.getDocumentId());

        // 문서 조회
        com.stolink.backend.domain.document.entity.Document document = documentRepository.findById(documentId)
                .orElse(null);
        if (document == null) {
            log.error("Document not found: {}", callback.getDocumentId());
            return;
        }

        // 실패 처리
        if (callback.isFailed()) {
            log.error("Document analysis failed for {}: {}", callback.getDocumentId(), callback.getError());
            document.updateAnalysisStatus(com.stolink.backend.domain.document.entity.Document.AnalysisStatus.FAILED);
            documentRepository.save(document);

            // AnalysisJob 상태 업데이트
            updateAnalysisJobStatus(documentId, callback.getTraceId(), AnalysisJob.JobStatus.FAILED, callback.getError(), callback.getProcessingTimeMs());
            return;
        }

        // 1. Section 저장
        saveSections(document, callback.getSections());

        // 2. 임시 캐릭터/이벤트/설정 저장 (기존 로직 재사용)
        Project project = document.getProject();
        if (callback.getCharacters() != null && !callback.getCharacters().isEmpty()) {
            saveCharacters(callback.getCharacters(), project);
        }
        if (callback.getEvents() != null && !callback.getEvents().isEmpty()) {
            saveEvents(callback.getEvents(), project);
        }
        if (callback.getSettings() != null && !callback.getSettings().isEmpty()) {
            saveSettings(callback.getSettings(), project);
        }

        // 3. 문서 상태 업데이트
        document.updateAnalysisStatus(com.stolink.backend.domain.document.entity.Document.AnalysisStatus.COMPLETED);
        documentRepository.save(document);

        // AnalysisJob 상태 업데이트
        updateAnalysisJobStatus(documentId, callback.getTraceId(), AnalysisJob.JobStatus.COMPLETED, null, callback.getProcessingTimeMs());

        // 4. 1차 Pass 완료 체크 및 2차 Pass 트리거
        checkAndTriggerGlobalMerge(project, callback.getTraceId());

        log.info("Document analysis callback processed for: {} (processing_time: {}ms)",
                callback.getDocumentId(), callback.getProcessingTimeMs());

        // SSE 알림 전송 (1차 Pass 진행률)
        sendProgressNotification(project);
    }

    /**
     * Section 저장
     */
    private void saveSections(com.stolink.backend.domain.document.entity.Document document,
            List<SectionDTO> sections) {
        if (sections == null || sections.isEmpty()) {
            log.info("No sections to save for document: {}", document.getId());
            return;
        }

        // 기존 Section 삭제 (재분석 시)
        sectionRepository.deleteAllByDocument(document);

        for (SectionDTO sectionDTO : sections) {
            com.stolink.backend.domain.document.entity.Section section = com.stolink.backend.domain.document.entity.Section
                    .builder()
                    .document(document)
                    .sequenceOrder(sectionDTO.getSequenceOrder())
                    .navTitle(sectionDTO.getNavTitle())
                    .content(sectionDTO.getContent())
                    .embedding(toFloatArray(sectionDTO.getEmbedding()))
                    .relatedCharactersJson(toJson(sectionDTO.getRelatedCharacters()))
                    .relatedEventsJson(toJson(sectionDTO.getRelatedEvents()))
                    .build();

            sectionRepository.save(section);
        }

        log.info("Saved {} sections for document: {}", sections.size(), document.getId());
    }

    /**
     * 1차 Pass 완료 체크 및 2차 Pass 트리거
     */
    private void checkAndTriggerGlobalMerge(Project project, String traceId) {
        UUID projectId = project.getId();

        // TEXT 문서 총 수
        long totalTextDocuments = documentRepository.countTextDocumentsByProjectId(projectId);

        // COMPLETED 상태 문서 수
        long completedDocuments = documentRepository.countByProjectIdAndTypeTextAndAnalysisStatus(
                projectId,
                com.stolink.backend.domain.document.entity.Document.AnalysisStatus.COMPLETED);

        log.info("Project {} - 1차 Pass 진행률: {}/{}", projectId, completedDocuments, totalTextDocuments);

        if (completedDocuments == totalTextDocuments && totalTextDocuments > 0) {
            log.info("Project {} - 모든 문서 분석 완료! 2차 Pass(글로벌 병합) 트리거", projectId);
            documentAnalysisPublisher.publishGlobalMerge(projectId, traceId);

            // SSE 알림: 글로벌 병합 시작
            sseEmitterService.sendStatus(projectId, new SseEmitterService.AnalysisStatusEvent(
                    "MERGING", (int) completedDocuments, (int) totalTextDocuments, "글로벌 병합 시작..."));
        }
    }

    // SSE 진행률 알림 헬퍼
    private void sendProgressNotification(Project project) {
        UUID projectId = project.getId();
        long total = documentRepository.countTextDocumentsByProjectId(projectId);
        long completed = documentRepository.countByProjectIdAndTypeTextAndAnalysisStatus(
                projectId, com.stolink.backend.domain.document.entity.Document.AnalysisStatus.COMPLETED);

        sseEmitterService.sendStatus(projectId, new SseEmitterService.AnalysisStatusEvent(
                "ANALYZING", (int) completed, (int) total,
                String.format("분석 진행 중: %d/%d 챕터", completed, total)));
    }

    /**
     * 글로벌 병합 결과 콜백 처리 (2차 Pass)
     *
     * Entity Resolution(캐릭터 병합) 결과를 적용합니다.
     */
    @Transactional
    public void handleGlobalMergeCallback(com.stolink.backend.domain.ai.dto.GlobalMergeCallbackDTO callback) {
        log.info("Processing global merge callback for project: {}, status: {}",
                callback.getProjectId(), callback.getStatus());

        if (!callback.isSuccess()) {
            log.error("Global merge failed for project {}: {}", callback.getProjectId(), callback.getError());
            return;
        }

        String projectIdStr = callback.getProjectId();

        // 캐릭터 병합 적용
        if (callback.getCharacterMerges() != null) {
            for (com.stolink.backend.domain.ai.dto.GlobalMergeCallbackDTO.CharacterMergeDTO merge : callback
                    .getCharacterMerges()) {
                applyCharacterMerge(merge, projectIdStr);
            }
        }

        // 일관성 보고서 로깅
        if (callback.getConsistencyReport() != null) {
            log.info("Global merge consistency report for project {}: {}", projectIdStr, callback.getConsistencyReport());
        }

        log.info("Global merge callback processed for project: {} (processing_time: {}ms)",
                callback.getProjectId(), callback.getProcessingTimeMs());

        // SSE 알림: 분석 완료
        try {
            UUID projectId = UUID.fromString(projectIdStr);
            long totalDocs = documentRepository.countTextDocumentsByProjectId(projectId);
            sseEmitterService.sendStatus(projectId, new SseEmitterService.AnalysisStatusEvent(
                    "COMPLETED", (int) totalDocs, (int) totalDocs, "분석이 완료되었습니다."));
        } catch (Exception e) {
            log.error("Failed to send completion SSE for project: {}", projectIdStr, e);
        }
    }

    /**
     * 캐릭터 병합 적용
     */
    private void applyCharacterMerge(com.stolink.backend.domain.ai.dto.GlobalMergeCallbackDTO.CharacterMergeDTO merge,
            String projectId) {
        String primaryId = merge.getPrimaryId();
        java.util.List<String> mergedIds = merge.getMergedIds();

        if (primaryId == null || mergedIds == null || mergedIds.isEmpty()) {
            return;
        }

        // Primary 캐릭터 조회
        Optional<Character> primaryCharOpt = characterRepository.findById(primaryId);
        if (primaryCharOpt.isEmpty()) {
            log.warn("Primary character not found for merge: {}", primaryId);
            return;
        }

        Character primaryChar = primaryCharOpt.get();

        // Aliases 통합
        java.util.Set<String> allAliases = new java.util.HashSet<>();
        String existingAliasesJson = primaryChar.getAliasesJson();
        if (existingAliasesJson != null) {
            try {
                java.util.List<String> existingAliases = objectMapper.readValue(existingAliasesJson,
                        objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, String.class));
                allAliases.addAll(existingAliases);
            } catch (Exception e) {
                log.warn("Failed to parse existing aliases: {}", e.getMessage());
            }
        }
        if (merge.getMergedAliases() != null) {
            allAliases.addAll(merge.getMergedAliases());
        }

        primaryChar.setAliasesJson(toJson(new java.util.ArrayList<>(allAliases)));
        characterRepository.save(primaryChar);

        // 중복 캐릭터 삭제
        for (String oldId : mergedIds) {
            try {
                characterRepository.mergeNodes(primaryId, oldId);
                log.info("Merged character: old character name {} -> primary character name {}", oldId, primaryId);
            } catch (Exception e) {
                log.warn("Failed to merge character {}: {}", oldId, e.getMessage());
            }
        }

        log.info("Applied character merge: {} <- {} (aliases: {})",
                primaryId, mergedIds, merge.getMergedAliases());
    }

    /**
     * AnalysisJob 상태 업데이트 헬퍼
     */
    private void updateAnalysisJobStatus(UUID documentId, String traceId, AnalysisJob.JobStatus status, String errorMessage, Long processingTimeMs) {
        List<AnalysisJob> activeJobs = analysisJobRepository.findByDocumentIdAndTraceIdAndStatus(documentId, traceId, AnalysisJob.JobStatus.PROCESSING);

        if (activeJobs.isEmpty()) {
            log.warn("No active PROCESSING job found for document: {} and traceId: {}", documentId, traceId);
            // 만약 PROCESSING이 아니더라도 가장 최근의 PENDING 작업을 찾아서 업데이트할 수도 있음
            return;
        }

        for (AnalysisJob job : activeJobs) {
            if (status == AnalysisJob.JobStatus.COMPLETED) {
                job.markAsCompleted(processingTimeMs);
            } else if (status == AnalysisJob.JobStatus.FAILED) {
                job.markAsFailed(errorMessage);
            }
            analysisJobRepository.save(job);
            log.info("AnalysisJob {} status updated to {}", job.getJobId(), status);
        }
    }

    private float[] toFloatArray(java.util.List<Double> embedding) {
        if (embedding == null) {
            return null;
        }
        float[] floatArray = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            floatArray[i] = embedding.get(i).floatValue();
        }
        return floatArray;
    }
}
