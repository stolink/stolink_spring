package com.stolink.backend.domain.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.ImageCallbackDTO;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.repository.CharacterRepository;

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
    private final com.stolink.backend.domain.character.repository.CharacterJpaRepository characterJpaRepository;
    private final EventRepository eventRepository;
    private final SettingRepository settingRepository;

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

        // 전체 결과 데이터 로깅 (디버깅용 - 사용자가 확인 가능하도록 INFO로 변경)
        log.info("Full analysis result for project {}: {}", projectIdStr, result);

        // 메타데이터에서 processing_time 추출
        Long processingTimeMs = extractProcessingTime(result);

        // 메타데이터 로깅
        logMetadata(result);

        // 1. 캐릭터 저장 (Neo4j & Postgres)
        saveCharacters(result, project);

        // 2. 관계 저장 (Neo4j)
        saveRelationships(result, projectIdStr);

        // 3. 감정 정보를 캐릭터에 업데이트 (Neo4j)
        updateEmotions(result, projectIdStr);

        // 4. 이벤트 저장 (PostgreSQL)
        saveEvents(result, project);

        // 5. 설정(장소) 저장 (PostgreSQL)
        saveSettings(result, project);

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
    private void saveCharacters(Map<String, Object> result, Project project) {
        String projectId = project.getId().toString();
        List<Map<String, Object>> characters = (List<Map<String, Object>>) result.get("characters");
        if (characters == null || characters.isEmpty()) {
            log.info("No characters to save");
            return;
        }

        for (Map<String, Object> charData : characters) {
            // Try to get name from profile first (as per expected.json schema)
            String name = null;
            Map<String, Object> profile = (Map<String, Object>) charData.get("profile");
            if (profile != null) {
                name = (String) profile.get("name");
            }
            // Fallback to top-level name if not in profile
            if (name == null) {
                name = (String) charData.get("name");
            }

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

            // PostgreSQL 저장 (AI 서버 호환용)
            try {
                saveCharacterToPostgres(charData, project);
            } catch (Exception e) {
                log.error("Failed to save character to Postgres: {}", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void saveCharacterToPostgres(Map<String, Object> charData, Project project) {
        // Name extraction logic
        String name = null;
        Map<String, Object> profile = (Map<String, Object>) charData.get("profile");
        if (profile != null) {
            name = (String) profile.get("name");
        }
        if (name == null) {
            name = (String) charData.get("name");
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
        entity.setCharacterId((String) charData.get("_id"));
        entity.setRole((String) charData.get("role"));
        entity.setStatus((String) charData.get("status"));

        // Profile fields (from profile object or top-level)
        // profile variable is already extracted above
        if (profile != null) {
            entity.setAge(profile.get("age") != null ? ((Number) profile.get("age")).intValue() : null);
            entity.setGender((String) profile.get("gender"));
            entity.setRace((String) profile.get("race"));
            entity.setMbti((String) profile.get("mbti"));
            entity.setBackstory((String) profile.get("backstory"));
            Map<String, Object> faction = (Map<String, Object>) profile.get("faction");
            if (faction != null) {
                entity.setFaction((String) faction.get("name"));
            }
        }

        try {
            // Aliases
            if (charData.get("aliases") != null)
                entity.setAliasesJson(objectMapper.writeValueAsString(charData.get("aliases")));

            // Profile full JSON
            if (profile != null)
                entity.setProfileJson(objectMapper.writeValueAsString(profile));

            // Appearance
            if (charData.get("appearance") != null)
                entity.setAppearanceJson(objectMapper.writeValueAsString(charData.get("appearance")));

            // Visual (legacy, same as appearance)
            if (charData.get("visual") != null)
                entity.setVisualJson(objectMapper.writeValueAsString(charData.get("visual")));
            else if (charData.get("appearance") != null)
                entity.setVisualJson(objectMapper.writeValueAsString(charData.get("appearance")));

            // Personality
            if (charData.get("personality") != null)
                entity.setPersonalityJson(objectMapper.writeValueAsString(charData.get("personality")));

            // Relations
            if (charData.get("relations") != null)
                entity.setRelationsJson(objectMapper.writeValueAsString(charData.get("relations")));

            // Current Mood
            if (charData.get("current_mood") != null)
                entity.setCurrentMoodJson(objectMapper.writeValueAsString(charData.get("current_mood")));

            // Meta
            if (charData.get("meta") != null)
                entity.setMetaJson(objectMapper.writeValueAsString(charData.get("meta")));

            // Embedding
            if (charData.get("embedding") != null)
                entity.setEmbeddingJson(objectMapper.writeValueAsString(charData.get("embedding")));

        } catch (JsonProcessingException e) {
            log.error("JSON processing error for character entity: {}", e.getMessage());
        }

        // Motivation and first appearance
        entity.setMotivation((String) charData.get("motivation"));
        entity.setFirstAppearance((String) charData.get("first_appearance"));

        characterJpaRepository.save(entity);
        log.info("Saved character to Postgres: {}", name);
    }

    /**
     * 캐릭터 JSON 필드 업데이트 (Neo4j)
     */
    @SuppressWarnings("unchecked")
    private void updateCharacterJsonFields(Character character, Map<String, Object> charData) {
        try {
            // AI generated ID
            character.setCharacterId((String) charData.get("_id"));

            // Profile fields
            Map<String, Object> profile = (Map<String, Object>) charData.get("profile");
            if (profile != null) {
                character.setAge(profile.get("age") != null ? ((Number) profile.get("age")).intValue() : null);
                character.setGender((String) profile.get("gender"));
                character.setRace((String) profile.get("race"));
                character.setMbti((String) profile.get("mbti"));
                character.setBackstory((String) profile.get("backstory"));
                Map<String, Object> faction = (Map<String, Object>) profile.get("faction");
                if (faction != null) {
                    character.setFaction((String) faction.get("name"));
                }
                character.setProfileJson(objectMapper.writeValueAsString(profile));
            }

            // Aliases
            if (charData.get("aliases") != null) {
                character.setAliasesJson(objectMapper.writeValueAsString(charData.get("aliases")));
            }

            // Appearance
            Map<String, Object> appearance = (Map<String, Object>) charData.get("appearance");
            if (appearance != null) {
                character.setAppearanceJson(objectMapper.writeValueAsString(appearance));
            }

            // Visual (legacy)
            Map<String, Object> visual = (Map<String, Object>) charData.get("visual");
            if (visual != null) {
                character.setVisualJson(objectMapper.writeValueAsString(visual));
            } else if (appearance != null) {
                character.setVisualJson(objectMapper.writeValueAsString(appearance));
            }

            // Personality
            Map<String, Object> personality = (Map<String, Object>) charData.get("personality");
            if (personality != null) {
                character.setPersonalityJson(objectMapper.writeValueAsString(personality));
            }

            // Relations
            if (charData.get("relations") != null) {
                character.setRelationsJson(objectMapper.writeValueAsString(charData.get("relations")));
            }

            // Current Mood
            Map<String, Object> currentMood = (Map<String, Object>) charData.get("current_mood");
            if (currentMood != null) {
                character.setCurrentMoodJson(objectMapper.writeValueAsString(currentMood));
            }

            // Meta
            if (charData.get("meta") != null) {
                character.setMetaJson(objectMapper.writeValueAsString(charData.get("meta")));
            }

            // Embedding
            if (charData.get("embedding") != null) {
                character.setEmbeddingJson(objectMapper.writeValueAsString(charData.get("embedding")));
            }

            // Simple string fields
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

            // New AI schema fields
            try {
                if (eventData.get("timestamp") != null) {
                    event.setTimestampJson(objectMapper.writeValueAsString(eventData.get("timestamp")));
                }
                if (eventData.get("changes_made") != null) {
                    event.setChangesJson(objectMapper.writeValueAsString(eventData.get("changes_made")));
                }
                if (eventData.get("embedding") != null) {
                    event.setEmbeddingJson(objectMapper.writeValueAsString(eventData.get("embedding")));
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize event JSON fields: {}", e.getMessage());
            }

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
            setting.setLocationName((String) settingData.get("location_name"));
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
            return;
        }

        // 1. Section 저장
        saveSections(document, callback.getSections());

        // 2. 임시 캐릭터/이벤트/설정 저장 (기존 로직 재사용)
        Project project = document.getProject();
        if (callback.getCharacters() != null && !callback.getCharacters().isEmpty()) {
            java.util.Map<String, Object> tempResult = new java.util.HashMap<>();
            tempResult.put("characters", callback.getCharacters());
            saveCharacters(tempResult, project);
        }
        if (callback.getEvents() != null && !callback.getEvents().isEmpty()) {
            java.util.Map<String, Object> tempResult = new java.util.HashMap<>();
            tempResult.put("events", callback.getEvents());
            saveEvents(tempResult, project);
        }
        if (callback.getSettings() != null && !callback.getSettings().isEmpty()) {
            java.util.Map<String, Object> tempResult = new java.util.HashMap<>();
            tempResult.put("settings", callback.getSettings());
            saveSettings(tempResult, project);
        }

        // 3. 문서 상태 업데이트
        document.updateAnalysisStatus(com.stolink.backend.domain.document.entity.Document.AnalysisStatus.COMPLETED);
        documentRepository.save(document);

        // 4. 1차 Pass 완료 체크 및 2차 Pass 트리거
        checkAndTriggerGlobalMerge(project, callback.getTraceId());

        log.info("Document analysis callback processed for: {} (processing_time: {}ms)",
                callback.getDocumentId(), callback.getProcessingTimeMs());
    }

    /**
     * Section 저장
     */
    private void saveSections(com.stolink.backend.domain.document.entity.Document document,
            java.util.List<com.stolink.backend.domain.ai.dto.DocumentAnalysisCallbackDTO.SectionDTO> sections) {
        if (sections == null || sections.isEmpty()) {
            log.info("No sections to save for document: {}", document.getId());
            return;
        }

        // 기존 Section 삭제 (재분석 시)
        sectionRepository.deleteAllByDocument(document);

        for (com.stolink.backend.domain.ai.dto.DocumentAnalysisCallbackDTO.SectionDTO sectionDTO : sections) {
            com.stolink.backend.domain.document.entity.Section section = com.stolink.backend.domain.document.entity.Section
                    .builder()
                    .document(document)
                    .sequenceOrder(sectionDTO.getSequenceOrder())
                    .navTitle(sectionDTO.getNavTitle())
                    .content(sectionDTO.getContent())
                    .embeddingJson(toJson(sectionDTO.getEmbedding()))
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
        }
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

        String projectId = callback.getProjectId();

        // 캐릭터 병합 적용
        if (callback.getCharacterMerges() != null) {
            for (com.stolink.backend.domain.ai.dto.GlobalMergeCallbackDTO.CharacterMergeDTO merge : callback
                    .getCharacterMerges()) {
                applyCharacterMerge(merge, projectId);
            }
        }

        // 일관성 보고서 로깅
        if (callback.getConsistencyReport() != null) {
            log.info("Global merge consistency report for project {}: {}", projectId, callback.getConsistencyReport());
        }

        log.info("Global merge callback processed for project: {} (processing_time: {}ms)",
                callback.getProjectId(), callback.getProcessingTimeMs());
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
                characterRepository.deleteById(oldId);
                log.info("Deleted merged character: {} (merged into {})", oldId, primaryId);
            } catch (Exception e) {
                log.warn("Failed to delete merged character {}: {}", oldId, e.getMessage());
            }
        }

        log.info("Applied character merge: {} <- {} (aliases: {})",
                primaryId, mergedIds, merge.getMergedAliases());
    }
}
