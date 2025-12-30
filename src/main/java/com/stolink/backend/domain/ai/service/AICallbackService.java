package com.stolink.backend.domain.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.ImageCallbackDTO;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.repository.CharacterRepository;
import com.stolink.backend.domain.dialogue.entity.Dialogue;
import com.stolink.backend.domain.dialogue.repository.DialogueRepository;
import com.stolink.backend.domain.event.entity.Event;
import com.stolink.backend.domain.event.repository.EventRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.setting.entity.Setting;
import com.stolink.backend.domain.setting.repository.SettingRepository;
import com.stolink.backend.domain.plot.entity.PlotIntegration;
import com.stolink.backend.domain.plot.repository.PlotIntegrationRepository;
import com.stolink.backend.domain.consistency.entity.ConsistencyReport;
import com.stolink.backend.domain.consistency.repository.ConsistencyReportRepository;
import com.stolink.backend.domain.validation.entity.ValidationResult;
import com.stolink.backend.domain.validation.repository.ValidationResultRepository;
import com.stolink.backend.domain.foreshadowing.entity.Foreshadowing;
import com.stolink.backend.domain.foreshadowing.repository.ForeshadowingRepository;
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
    private final EventRepository eventRepository;
    private final SettingRepository settingRepository;
    private final DialogueRepository dialogueRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final PlotIntegrationRepository plotIntegrationRepository;
    private final ConsistencyReportRepository consistencyReportRepository;
    private final ValidationResultRepository validationResultRepository;
    private final ForeshadowingRepository foreshadowingRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.callback-base-url}")
    private String callbackBaseUrl;

    /**
     * 분석 결과 콜백 처리 (Multi-Agent 파이프라인 결과)
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

        Map<String, Object> result = callback.getResult();
        if (result == null) {
            log.warn("No result in callback for job: {}", callback.getJobId());
            job.markAsFailed("No result in callback");
            analysisJobRepository.save(job);
            return;
        }

        // Job에서 Project와 projectId 획득
        Project project = job.getProject();
        String projectIdStr = project.getId().toString();

        // 전체 결과 데이터 로깅 (디버깅용)
        log.debug("Full analysis result for project {}: {}", projectIdStr, result);

        // 메타데이터에서 processing_time 추출
        Long processingTimeMs = extractProcessingTime(result);

        // 메타데이터 로깅
        logMetadata(result);

        // 1. 캐릭터 저장 (Neo4j)
        saveCharacters(result, projectIdStr);

        // 2. 관계 저장 (Neo4j)
        saveRelationships(result, projectIdStr);

        // 3. 감정 정보를 캐릭터에 업데이트 (Neo4j)
        updateEmotions(result, projectIdStr);

        // 4. 이벤트 저장 (PostgreSQL)
        saveEvents(result, project);

        // 5. 설정(장소) 저장 (PostgreSQL)
        saveSettings(result, project);

        // 6. 대화 저장 (PostgreSQL)
        saveDialogues(result, project);

        // 7. 플롯 통합 저장 (PostgreSQL)
        savePlotIntegration(result, project, job.getDocumentId());

        // 8. 일관성 보고서 저장 (PostgreSQL)
        logConsistencyReport(result);
        saveConsistencyReport(result, project, callback.getJobId());

        // 9. 검증 결과 저장 (PostgreSQL)
        saveValidationResult(result, project, callback.getJobId());

        // 10. 복선 저장 (PostgreSQL)
        saveForeshadowing(result, project);

        // Job 완료 처리
        job.markAsCompleted(processingTimeMs);
        analysisJobRepository.save(job);

        log.info("Analysis callback processed successfully for job: {}", callback.getJobId());
    }

    /**
     * 메타데이터에서 processing_time_ms 추출
     */
    @SuppressWarnings("unchecked")
    private Long extractProcessingTime(Map<String, Object> result) {
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
        if (metadata != null && metadata.get("processing_time_ms") != null) {
            return ((Number) metadata.get("processing_time_ms")).longValue();
        }
        return null;
    }

    /**
     * 메타데이터 로깅
     */
    @SuppressWarnings("unchecked")
    private void logMetadata(Map<String, Object> result) {
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
        if (metadata != null) {
            log.info("Analysis metadata - processing_time_ms: {}, tokens_used: {}, trace_id: {}, agents: {}",
                    metadata.get("processing_time_ms"),
                    metadata.get("tokens_used"),
                    metadata.get("trace_id"),
                    metadata.get("agents_executed"));
        }
    }

    /**
     * 캐릭터 저장 (Neo4j)
     */
    @SuppressWarnings("unchecked")
    private void saveCharacters(Map<String, Object> result, String projectId) {
        List<Map<String, Object>> characters = (List<Map<String, Object>>) result.get("characters");
        if (characters == null || characters.isEmpty()) {
            log.info("No characters to save");
            return;
        }

        for (Map<String, Object> charData : characters) {
            String name = (String) charData.get("name");
            String role = (String) charData.get("role");
            String status = (String) charData.get("status");

            if (name == null || name.isBlank()) {
                log.warn("Skipping character with empty name");
                continue;
            }

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
        }
    }

    /**
     * 캐릭터 JSON 필드 업데이트
     */
    @SuppressWarnings("unchecked")
    private void updateCharacterJsonFields(Character character, Map<String, Object> charData) {
        try {
            Map<String, Object> visual = (Map<String, Object>) charData.get("visual");
            if (visual != null) {
                character.setVisualJson(objectMapper.writeValueAsString(visual));
            }

            Map<String, Object> personality = (Map<String, Object>) charData.get("personality");
            if (personality != null) {
                character.setPersonalityJson(objectMapper.writeValueAsString(personality));
            }

            Map<String, Object> currentMood = (Map<String, Object>) charData.get("current_mood");
            if (currentMood != null) {
                character.setCurrentMoodJson(objectMapper.writeValueAsString(currentMood));
            }

            // v2.0 스키마 필드
            String motivation = (String) charData.get("motivation");
            if (motivation != null) {
                character.setMotivation(motivation);
            }

            String firstAppearance = (String) charData.get("first_appearance");
            if (firstAppearance != null) {
                character.setFirstAppearance(firstAppearance);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize character data to JSON: {}", e.getMessage());
        }
    }

    /**
     * 관계 저장 (Neo4j)
     */
    @SuppressWarnings("unchecked")
    private void saveRelationships(Map<String, Object> result, String projectId) {
        List<Map<String, Object>> relationships = (List<Map<String, Object>>) result.get("relationships");
        if (relationships == null || relationships.isEmpty()) {
            log.info("No relationships to save");
            return;
        }

        for (Map<String, Object> relData : relationships) {
            String sourceName = (String) relData.get("source");
            String targetName = (String) relData.get("target");
            String relationType = (String) relData.get("relation_type");
            Integer strength = relData.get("strength") != null
                    ? ((Number) relData.get("strength")).intValue()
                    : 5;
            String description = (String) relData.get("description");

            if (sourceName == null || targetName == null) {
                log.warn("Skipping relationship with missing source/target");
                continue;
            }

            Optional<Character> sourceChar = characterRepository.findByNameAndProjectId(sourceName, projectId);
            Optional<Character> targetChar = characterRepository.findByNameAndProjectId(targetName, projectId);

            if (sourceChar.isEmpty() || targetChar.isEmpty()) {
                log.warn("Source or target character not found: {} -> {}", sourceName, targetName);
                continue;
            }

            try {
                characterRepository.createRelationship(
                        sourceChar.get().getId(),
                        targetChar.get().getId(),
                        relationType != null ? relationType.toLowerCase() : "related",
                        strength,
                        description);
                log.info("Created relationship: {} -[{}]-> {}", sourceName, relationType, targetName);
            } catch (Exception e) {
                log.error("Failed to create relationship: {} -> {}: {}", sourceName, targetName, e.getMessage());
            }
        }
    }

    /**
     * 이벤트 저장 (PostgreSQL)
     */
    @SuppressWarnings("unchecked")
    private void saveEvents(Map<String, Object> result, Project project) {
        List<Map<String, Object>> events = (List<Map<String, Object>>) result.get("events");
        if (events == null || events.isEmpty()) {
            log.info("No events to save");
            return;
        }

        for (Map<String, Object> eventData : events) {
            String eventId = (String) eventData.get("event_id");
            String eventTypeStr = (String) eventData.get("event_type");
            String narrativeSummary = (String) eventData.get("narrative_summary");
            String description = (String) eventData.get("description");
            String visualScene = (String) eventData.get("visual_scene");
            String cameraAngle = (String) eventData.get("camera_angle");
            String locationRef = (String) eventData.get("location_ref");
            String prevEventId = (String) eventData.get("prev_event_id");
            Integer importance = eventData.get("importance") != null
                    ? ((Number) eventData.get("importance")).intValue()
                    : 5;
            Boolean isForeshadowing = (Boolean) eventData.get("is_foreshadowing");

            // participants를 JSON 문자열로
            String participantsJson = null;
            List<String> participants = (List<String>) eventData.get("participants");
            if (participants != null) {
                try {
                    participantsJson = objectMapper.writeValueAsString(participants);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize participants: {}", e.getMessage());
                }
            }

            // EventType 변환
            Event.EventType eventType = null;
            if (eventTypeStr != null) {
                try {
                    eventType = Event.EventType.valueOf(eventTypeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown event type: {}", eventTypeStr);
                }
            }

            // 기존 이벤트 조회 또는 새로 생성
            Optional<Event> existingEvent = eventRepository.findByProjectAndEventId(project, eventId);

            Event event;
            if (existingEvent.isPresent()) {
                event = existingEvent.get();
            } else {
                event = Event.builder()
                        .project(project)
                        .eventId(eventId)
                        .build();
            }

            event.setEventType(eventType);
            event.setNarrativeSummary(narrativeSummary);
            event.setDescription(description);
            event.setVisualScene(visualScene);
            event.setCameraAngle(cameraAngle);
            event.setLocationRef(locationRef);
            event.setPrevEventId(prevEventId);
            event.setImportance(importance);
            event.setIsForeshadowing(isForeshadowing != null ? isForeshadowing : false);
            event.setParticipants(participantsJson);

            eventRepository.save(event);
            log.info("Saved event: {} ({})", eventId, narrativeSummary);
        }
    }

    /**
     * 설정(장소) 저장 (PostgreSQL)
     */
    @SuppressWarnings("unchecked")
    private void saveSettings(Map<String, Object> result, Project project) {
        List<Map<String, Object>> settings = (List<Map<String, Object>>) result.get("settings");
        if (settings == null || settings.isEmpty()) {
            log.info("No settings to save");
            return;
        }

        for (Map<String, Object> settingData : settings) {
            String settingId = (String) settingData.get("setting_id");
            String name = (String) settingData.get("name");
            String locationTypeStr = (String) settingData.get("location_type");
            String visualPrompt = (String) settingData.get("static_visual_prompt");
            if (visualPrompt == null) {
                visualPrompt = (String) settingData.get("visual_background");
            }
            String timeOfDay = (String) settingData.get("time_of_day");
            String lightingDescription = (String) settingData.get("lighting_description");
            if (lightingDescription == null) {
                lightingDescription = (String) settingData.get("lighting");
            }
            String atmosphereKeywords = (String) settingData.get("atmosphere_keywords");
            if (atmosphereKeywords == null) {
                atmosphereKeywords = (String) settingData.get("atmosphere");
            }
            String weatherCondition = (String) settingData.get("weather_condition");
            if (weatherCondition == null) {
                weatherCondition = (String) settingData.get("weather");
            }
            Boolean isPrimary = (Boolean) settingData.get("is_primary_location");
            if (isPrimary == null) {
                isPrimary = (Boolean) settingData.get("is_primary");
            }
            String storySignificance = (String) settingData.get("story_significance");
            if (storySignificance == null) {
                storySignificance = (String) settingData.get("significance");
            }

            // static_objects를 JSON 문자열로
            String staticObjectsJson = null;
            List<String> staticObjects = (List<String>) settingData.get("static_objects");
            if (staticObjects == null) {
                staticObjects = (List<String>) settingData.get("notable_features");
            }
            if (staticObjects != null) {
                try {
                    staticObjectsJson = objectMapper.writeValueAsString(staticObjects);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize static_objects: {}", e.getMessage());
                }
            }

            // LocationType 변환
            Setting.LocationType locationType = null;
            if (locationTypeStr != null) {
                try {
                    locationType = Setting.LocationType.valueOf(locationTypeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    locationType = Setting.LocationType.OTHER;
                    log.warn("Unknown location type: {}, defaulting to OTHER", locationTypeStr);
                }
            }

            // 기존 설정 조회 또는 새로 생성
            Optional<Setting> existingSetting = settingRepository.findByProjectAndName(project, name);

            Setting setting;
            if (existingSetting.isPresent()) {
                setting = existingSetting.get();
            } else {
                setting = Setting.builder()
                        .project(project)
                        .settingId(settingId)
                        .name(name)
                        .build();
            }

            setting.setLocationType(locationType);
            setting.setVisualPrompt(visualPrompt);
            setting.setVisualBackground((String) settingData.get("visual_background"));
            setting.setTimeOfDay(timeOfDay);
            setting.setLightingDescription(lightingDescription);
            setting.setAtmosphereKeywords(atmosphereKeywords);
            setting.setWeatherCondition(weatherCondition);
            setting.setArtStyle((String) settingData.get("art_style"));
            setting.setDescription((String) settingData.get("description"));
            setting.setIsPrimaryLocation(isPrimary != null ? isPrimary : false);
            setting.setStorySignificance(storySignificance);
            setting.setStaticObjects(staticObjectsJson);

            settingRepository.save(setting);
            log.info("Saved setting: {} ({})", name, locationType);
        }
    }

    /**
     * 대화 저장 (PostgreSQL)
     */
    @SuppressWarnings("unchecked")
    private void saveDialogues(Map<String, Object> result, Project project) {
        Map<String, Object> dialoguesData = (Map<String, Object>) result.get("dialogues");
        if (dialoguesData == null) {
            log.info("No dialogues to save");
            return;
        }

        List<Map<String, Object>> keyDialogues = (List<Map<String, Object>>) dialoguesData.get("key_dialogues");
        if (keyDialogues == null || keyDialogues.isEmpty()) {
            log.info("No key dialogues to save");
            return;
        }

        for (Map<String, Object> dialogueData : keyDialogues) {
            String dialogueId = (String) dialogueData.get("dialogue_id");
            String speaker = (String) dialogueData.get("speaker");
            String listener = (String) dialogueData.get("listener");
            String line = (String) dialogueData.get("line");
            String content = (String) dialogueData.get("content");
            String significance = (String) dialogueData.get("significance");
            String subtext = (String) dialogueData.get("subtext");
            String emotion = (String) dialogueData.get("emotion");

            // participants를 JSON 문자열로
            String participantsJson = null;
            List<String> participants = (List<String>) dialogueData.get("participants");
            if (participants != null) {
                try {
                    participantsJson = objectMapper.writeValueAsString(participants);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize participants: {}", e.getMessage());
                }
            }

            // 기존 대화 조회 또는 새로 생성
            Optional<Dialogue> existingDialogue = dialogueRepository.findByProjectAndDialogueId(project, dialogueId);

            Dialogue dialogue;
            if (existingDialogue.isPresent()) {
                dialogue = existingDialogue.get();
            } else {
                dialogue = Dialogue.builder()
                        .project(project)
                        .dialogueId(dialogueId)
                        .build();
            }

            dialogue.setSpeaker(speaker);
            dialogue.setListener(listener);
            dialogue.setLine(line);
            dialogue.setContent(content);
            dialogue.setSignificance(significance);
            dialogue.setSubtext(subtext);
            dialogue.setEmotion(emotion);
            dialogue.setParticipants(participantsJson);

            dialogueRepository.save(dialogue);
            log.info("Saved dialogue: {} (speaker: {}, listener: {})", dialogueId, speaker, listener);
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
    @SuppressWarnings("unchecked")
    private void logConsistencyReport(Map<String, Object> result) {
        Map<String, Object> consistencyReport = (Map<String, Object>) result.get("consistency_report");
        if (consistencyReport != null) {
            Object overallScore = consistencyReport.get("overall_score");
            Boolean requiresReextraction = (Boolean) consistencyReport.get("requires_reextraction");
            List<?> conflicts = (List<?>) consistencyReport.get("conflicts");
            List<?> warnings = (List<?>) consistencyReport.get("warnings");

            log.info("Consistency report - score: {}, requires_reextraction: {}, conflicts: {}, warnings: {}",
                    overallScore, requiresReextraction,
                    conflicts != null ? conflicts.size() : 0,
                    warnings != null ? warnings.size() : 0);
        }
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
            return;
        }

        String characterId = callback.getCharacterId().toString();
        characterRepository.findById(characterId).ifPresent(character -> {
            character.setImageUrl(callback.getImageUrl());
            characterRepository.save(character);
            log.info("Updated character {} with image URL: {}", characterId, callback.getImageUrl());
        });
    }

    /**
     * 플롯 통합 저장 (PostgreSQL)
     */
    @SuppressWarnings("unchecked")
    private void savePlotIntegration(Map<String, Object> result, Project project, UUID documentId) {
        Map<String, Object> plotData = (Map<String, Object>) result.get("plot_integration");
        if (plotData == null) {
            log.info("No plot_integration to save");
            return;
        }

        try {
            // plot_summary 추출
            Map<String, Object> plotSummary = (Map<String, Object>) plotData.get("plot_summary");
            String narrative = plotSummary != null ? (String) plotSummary.get("narrative") : null;
            String centralConflict = plotSummary != null ? (String) plotSummary.get("central_conflict") : null;

            // overall_tension
            Double overallTension = plotData.get("overall_tension") != null
                    ? ((Number) plotData.get("overall_tension")).doubleValue()
                    : null;

            PlotIntegration plot = PlotIntegration.builder()
                    .project(project)
                    .documentId(documentId)
                    .narrative(narrative)
                    .centralConflict(centralConflict)
                    .overallTension(overallTension)
                    .narrativeBeatsJson(toJson(plotData.get("narrative_beats")))
                    .tensionCurveJson(toJson(plotData.get("tension_curve")))
                    .threeActStructureJson(toJson(plotData.get("three_act_structure")))
                    .foreshadowingJson(toJson(plotData.get("foreshadowing")))
                    .multimediaSummaryJson(toJson(plotData.get("multimedia_summary")))
                    .build();

            plotIntegrationRepository.save(plot);
            log.info("Saved plot integration for project: {}", project.getId());
        } catch (Exception e) {
            log.error("Failed to save plot integration: {}", e.getMessage());
        }
    }

    /**
     * 일관성 보고서 저장 (PostgreSQL)
     */
    @SuppressWarnings("unchecked")
    private void saveConsistencyReport(Map<String, Object> result, Project project, String jobId) {
        Map<String, Object> reportData = (Map<String, Object>) result.get("consistency_report");
        if (reportData == null) {
            log.info("No consistency_report to save");
            return;
        }

        try {
            Integer overallScore = reportData.get("overall_score") != null
                    ? ((Number) reportData.get("overall_score")).intValue()
                    : null;
            Boolean requiresReextraction = (Boolean) reportData.get("requires_reextraction");

            ConsistencyReport report = ConsistencyReport.builder()
                    .project(project)
                    .jobId(jobId)
                    .overallScore(overallScore)
                    .requiresReextraction(requiresReextraction != null ? requiresReextraction : false)
                    .conflictsJson(toJson(reportData.get("conflicts")))
                    .warningsJson(toJson(reportData.get("warnings")))
                    .resolutionSummaryJson(toJson(reportData.get("resolution_summary")))
                    .neo4jValidationJson(toJson(reportData.get("neo4j_validation")))
                    .build();

            consistencyReportRepository.save(report);
            log.info("Saved consistency report for job: {}, score: {}", jobId, overallScore);
        } catch (Exception e) {
            log.error("Failed to save consistency report: {}", e.getMessage());
        }
    }

    /**
     * 검증 결과 저장 (PostgreSQL)
     */
    @SuppressWarnings("unchecked")
    private void saveValidationResult(Map<String, Object> result, Project project, String jobId) {
        Map<String, Object> validationData = (Map<String, Object>) result.get("validation");
        if (validationData == null) {
            log.info("No validation to save");
            return;
        }

        try {
            Boolean isValid = (Boolean) validationData.get("is_valid");
            Integer qualityScore = validationData.get("quality_score") != null
                    ? ((Number) validationData.get("quality_score")).intValue()
                    : null;
            String action = (String) validationData.get("action");
            String actionDescription = (String) validationData.get("action_description");
            Double averageCompleteness = validationData.get("average_completeness") != null
                    ? ((Number) validationData.get("average_completeness")).doubleValue()
                    : null;
            Integer errorCount = validationData.get("error_count") != null
                    ? ((Number) validationData.get("error_count")).intValue()
                    : 0;
            Integer warningCount = validationData.get("warning_count") != null
                    ? ((Number) validationData.get("warning_count")).intValue()
                    : 0;
            Double executionTimeMs = validationData.get("execution_time_ms") != null
                    ? ((Number) validationData.get("execution_time_ms")).doubleValue()
                    : null;

            ValidationResult validation = ValidationResult.builder()
                    .project(project)
                    .jobId(jobId)
                    .isValid(isValid != null ? isValid : true)
                    .qualityScore(qualityScore)
                    .action(action)
                    .actionDescription(actionDescription)
                    .averageCompleteness(averageCompleteness)
                    .errorCount(errorCount)
                    .warningCount(warningCount)
                    .dataCompletenessJson(toJson(validationData.get("data_completeness")))
                    .validationDetailsJson(toJson(validationData.get("validation_details")))
                    .executionTimeMs(executionTimeMs)
                    .build();

            validationResultRepository.save(validation);
            log.info("Saved validation result for job: {}, quality_score: {}", jobId, qualityScore);
        } catch (Exception e) {
            log.error("Failed to save validation result: {}", e.getMessage());
        }
    }

    /**
     * 복선 저장 (PostgreSQL) - plot_integration.foreshadowing에서 추출
     */
    @SuppressWarnings("unchecked")
    private void saveForeshadowing(Map<String, Object> result, Project project) {
        Map<String, Object> plotData = (Map<String, Object>) result.get("plot_integration");
        if (plotData == null) {
            return;
        }

        List<Map<String, Object>> foreshadowingList = (List<Map<String, Object>>) plotData.get("foreshadowing");
        if (foreshadowingList == null || foreshadowingList.isEmpty()) {
            log.info("No foreshadowing to save");
            return;
        }

        for (Map<String, Object> fsData : foreshadowingList) {
            String foreshadowId = (String) fsData.get("foreshadow_id");
            String hintText = (String) fsData.get("hint_text");
            String predictedOutcome = (String) fsData.get("predicted_outcome");
            Integer confidence = fsData.get("confidence") != null
                    ? ((Number) fsData.get("confidence")).intValue()
                    : null;

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
}
