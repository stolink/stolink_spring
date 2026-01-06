package com.stolink.backend.domain.ai.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.ImageCallbackDTO;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.entity.CallbackLog;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.ai.repository.CallbackLogRepository;
import com.stolink.backend.domain.character.entity.ImageGenerationTask;
import com.stolink.backend.domain.character.entity.RelationshipEntity;
import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.repository.CharacterRepository;
import com.stolink.backend.domain.character.repository.ImageGenerationTaskRepository;
import com.stolink.backend.domain.character.repository.RelationshipRepository;
import com.stolink.backend.domain.consistency.entity.ConsistencyReport;
import com.stolink.backend.domain.consistency.repository.ConsistencyReportRepository;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.event.entity.EventEntity;
import com.stolink.backend.domain.event.node.Event;
import com.stolink.backend.domain.event.repository.EventJpaRepository;
import com.stolink.backend.domain.event.repository.EventNeo4jRepository;
import com.stolink.backend.domain.foreshadowing.entity.Foreshadowing;
import com.stolink.backend.domain.foreshadowing.repository.ForeshadowingRepository;
import com.stolink.backend.domain.plot.entity.PlotIntegration;
import com.stolink.backend.domain.plot.repository.PlotIntegrationRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.setting.entity.SettingEntity;
import com.stolink.backend.domain.setting.node.Setting;
import com.stolink.backend.domain.setting.repository.SettingNeo4jRepository;
import com.stolink.backend.domain.setting.repository.SettingRepository;
import com.stolink.backend.domain.validation.entity.ValidationResult;
import com.stolink.backend.domain.validation.repository.ValidationResultRepository;
import com.stolink.backend.global.sse.SseEmitterService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final DocumentRepository documentRepository;
    private final EventNeo4jRepository eventNeo4jRepository;
    private final EventJpaRepository eventJpaRepository;
    private final RelationshipRepository relationshipRepository;

    private final SettingNeo4jRepository settingNeo4jRepository;
    private final SettingRepository settingRepository;
    private final ImageGenerationTaskRepository imageGenerationTaskRepository;

    private final AnalysisJobRepository analysisJobRepository;
    private final com.stolink.backend.domain.document.repository.SectionRepository sectionRepository;
    private final DocumentAnalysisPublisher documentAnalysisPublisher;
    private final PlotIntegrationRepository plotIntegrationRepository;
    private final ConsistencyReportRepository consistencyReportRepository;
    private final ValidationResultRepository validationResultRepository;
    private final ForeshadowingRepository foreshadowingRepository;
    private final ProjectRepository projectRepository;

    private final CallbackLogRepository callbackLogRepository;
    private final ObjectMapper objectMapper;
    private final SseEmitterService sseEmitterService;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.ai.callback-base-url}")
    private String callbackBaseUrl;

    /**
     * 분석 결과 콜백 처리 (Multi-Agent 파이프라인 결과)
     */
    @Transactional
    public void handleAnalysisCallback(AnalysisCallbackDTO callback) {
        // Clear JPA L1 cache to ensure fresh reads of AI-written data
        entityManager.clear();

        log.info("Processing analysis callback for job: {}, status: {}",
                callback.getJobId(), callback.getStatus());

        // Idempotent check: skip if already processed
        if (callbackLogRepository.existsByJobId(callback.getJobId())) {
            log.warn("Duplicate callback ignored: {}", callback.getJobId());
            return;
        }

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

            // Document 상태 업데이트 (FAILED)
            updateDocumentStatus(job.getDocumentId(), Document.AnalysisStatus.FAILED);
            return;
        }

        Map<String, Object> result = callback.getResult();
        if (result == null) {
            log.warn("No result in callback for job: {}", callback.getJobId());
            job.markAsFailed("No result in callback");
            analysisJobRepository.save(job);

            // Document 상태 업데이트 (FAILED)
            updateDocumentStatus(job.getDocumentId(), Document.AnalysisStatus.FAILED);
            return;
        }

        // Job에서 Project와 projectId 획득
        // Job에서 Project와 projectId 획득 및 영속성 컨텍스트 재진입
        Project projectProxy = job.getProject();
        Project project = projectRepository.findById(projectProxy.getId())
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectProxy.getId()));
        String projectIdStr = project.getId().toString();

        log.debug("Handling callback for job {}, Project ID: {}", callback.getJobId(), projectIdStr);
        if (result.containsKey("analysis_type")) {
            log.debug("Analysis Type: {}", result.get("analysis_type"));
        }

        // 메타데이터에서 processing_time 추출
        Long processingTimeMs = extractProcessingTime(result);

        // 메타데이터 로깅
        logMetadata(result);

        // 1. 캐릭터 저장 (Neo4j & Postgres)
        saveCharacters(result, project);

        // 2. 관계 저장 (Neo4j & Postgres)
        saveRelationships(result, project);

        // 3. 감정 정보를 캐릭터에 업데이트 (Neo4j)
        updateEmotions(result, projectIdStr);

        // 4. 이벤트 저장 (PostgreSQL)
        saveEvents(result, project, job.getDocumentId());

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

        // Document 상태 업데이트 (COMPLETED)
        updateDocumentStatus(job.getDocumentId(), Document.AnalysisStatus.COMPLETED);

        // 콜백 처리 로그 저장 (idempotent 처리용)
        callbackLogRepository.save(CallbackLog.builder()
                .jobId(callback.getJobId())
                .messageType("DOCUMENT_ANALYSIS")
                .status(callback.getStatus())
                .processedAt(java.time.LocalDateTime.now())
                .projectId(project.getId())
                .build());

        log.info("Analysis callback processed successfully for job: {}", callback.getJobId());
    }

    // Document Status Update Helper
    private void updateDocumentStatus(UUID documentId, Document.AnalysisStatus status) {
        if (documentId == null)
            return;
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.updateAnalysisStatus(status);
            documentRepository.save(doc);
            log.info("Updated Document {} status to {}", documentId, status);
        });
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
            log.debug("No characters to save");
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

            Optional<Character> existingChar = characterRepository.findAllByNameAndProjectId(name, projectId).stream()
                    .findFirst();

            if (existingChar.isPresent()) {
                Character character = existingChar.get();
                character.setRole(role);
                character.setStatus(status);
                updateCharacterJsonFields(character, charData);
                characterRepository.save(character);
                log.debug("Updated character: {} (id: {})", name, character.getId());
            } else {
                Character character = Character.builder()
                        .projectId(projectId)
                        .name(name)
                        .role(role)
                        .status(status)
                        .build();
                updateCharacterJsonFields(character, charData);
                character = characterRepository.save(character);
                log.debug("Created character: {} (id: {})", name, character.getId());
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
        // 중복 캐릭터가 있어도 첫 번째 결과만 사용 (안전한 조회)
        java.util.List<com.stolink.backend.domain.character.entity.CharacterEntity> existingEntities = characterJpaRepository
                .findAllByProjectAndName(project, name);
        com.stolink.backend.domain.character.entity.CharacterEntity entity = existingEntities.isEmpty()
                ? com.stolink.backend.domain.character.entity.CharacterEntity.builder()
                        .project(project)
                        .name(name)
                        .build()
                : existingEntities.get(0);

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
        log.debug("Saved character to Postgres: {}", name);
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
     * 관계 저장 (Neo4j & PostgreSQL)
     */
    @SuppressWarnings("unchecked")
    private void saveRelationships(Map<String, Object> result, Project project) {
        String projectId = project.getId().toString();
        List<Map<String, Object>> relationships = (List<Map<String, Object>>) result.get("relationships");
        if (relationships == null || relationships.isEmpty()) {
            log.debug("No relationships to save");
            return;
        }

        // Clean up existing Postgres relationships for this project (optional, or just
        // append/update?)
        // Since callbacks might be partial or full, full replacement is safer for
        // consistency if re-running.
        // However, Neo4j logic accumulates. Let's assume append for now or handled by
        // IDs if we had them.
        // Relationships usually don't have stable IDs from AI unless generated.
        // For now, let's just insert.

        for (Map<String, Object> relData : relationships) {
            String sourceName = (String) relData.get("source");
            String targetName = (String) relData.get("target");
            String relationType = (String) relData.get("relation_type");
            Integer strength = relData.get("strength") != null
                    ? ((Number) relData.get("strength")).intValue()
                    : 5;
            String description = (String) relData.get("description");
            Boolean bidirectional = (Boolean) relData.get("bidirectional");

            if (sourceName == null || targetName == null) {
                log.warn("Skipping relationship with missing source/target");
                continue;
            }

            // Neo4j Processing
            Optional<Character> sourceChar = characterRepository.findAllByNameAndProjectId(sourceName, projectId)
                    .stream().findFirst();
            Optional<Character> targetChar = characterRepository.findAllByNameAndProjectId(targetName, projectId)
                    .stream().findFirst();

            if (sourceChar.isPresent() && targetChar.isPresent()) {
                try {
                    characterRepository.createRelationship(
                            sourceChar.get().getId(),
                            targetChar.get().getId(),
                            relationType != null ? relationType.toLowerCase() : "related",
                            strength,
                            description,
                            bidirectional != null ? bidirectional : false);
                    log.debug("Created Neo4j relationship: {} -[{}]-> {}", sourceName, relationType, targetName);
                } catch (Exception e) {
                    log.error("Failed to create Neo4j relationship: {} -> {}: {}", sourceName, targetName,
                            e.getMessage());
                }
            } else {
                log.warn("Source or target character not found in Neo4j: {} -> {}", sourceName, targetName);
            }

            // PostgreSQL Processing
            // 중복 캐릭터가 있어도 첫 번째 결과만 사용 (안전한 조회)
            java.util.List<com.stolink.backend.domain.character.entity.CharacterEntity> sourceList = characterJpaRepository
                    .findAllByProjectAndName(project, sourceName);
            java.util.List<com.stolink.backend.domain.character.entity.CharacterEntity> targetList = characterJpaRepository
                    .findAllByProjectAndName(project, targetName);
            com.stolink.backend.domain.character.entity.CharacterEntity sourceEntity = sourceList.isEmpty() ? null
                    : sourceList.get(0);
            com.stolink.backend.domain.character.entity.CharacterEntity targetEntity = targetList.isEmpty() ? null
                    : targetList.get(0);

            if (sourceEntity != null && targetEntity != null) {
                RelationshipEntity relEntity = RelationshipEntity.builder()
                        .project(project)
                        .sourceCharacter(sourceEntity)
                        .targetCharacter(targetEntity)
                        .sourceName(sourceName)
                        .targetName(targetName)
                        .relationType(relationType)
                        .strength(strength)
                        .description(description)
                        .bidirectional(bidirectional != null ? bidirectional : false)
                        .build();
                relationshipRepository.save(relEntity);
                log.debug("Saved Postgres relationship: {} -> {}", sourceName, targetName);
            } else {
                log.warn("Source or target character entity not found in Postgres: {} -> {}", sourceName, targetName);
                // Still save with names if entities not found? The entity enforces fields.
                // We can fallback to just names if we change entity definition, but for now we
                // require entities or assume they exist.
                // If characters were just saved in saveCharacters(), they should exist.
            }
        }
    }

    /**
     * 이벤트 저장 (Neo4j)
     */
    @SuppressWarnings("unchecked")
    private void saveEvents(Map<String, Object> result, Project project, UUID jobDocumentId) {
        List<Map<String, Object>> events = (List<Map<String, Object>>) result.get("events");
        log.debug("saveEvents called. Project ID: {}, Events count: {}", project.getId(),
                events != null ? events.size() : "null");
        if (events == null || events.isEmpty()) {
            log.debug("No events to save");

            return;
        }

        String projectId = project.getId().toString();

        for (Map<String, Object> eventData : events) {
            String eventId = (String) eventData.get("event_id");
            String eventType = (String) eventData.get("event_type");
            String narrativeSummary = (String) eventData.get("narrative_summary");
            String description = (String) eventData.get("description");
            String visualScene = (String) eventData.get("visual_scene");
            String cameraAngle = (String) eventData.get("camera_angle");
            String locationRef = (String) eventData.get("location_ref");
            String prevEventId = (String) eventData.get("prev_event_id");
            Integer importance = eventData.get("importance") != null
                    ? ((Number) eventData.get("importance")).intValue()
                    : 5;

            // Extract document_id from AI payload (required for DB NOT NULL constraint)
            UUID documentId = null;
            String docIdStr = (String) eventData.get("document_id");
            if (docIdStr != null && !docIdStr.isBlank()) {
                try {
                    documentId = UUID.fromString(docIdStr);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid document_id format: {}", docIdStr);
                }
            }
            if (documentId == null) {
                documentId = jobDocumentId;
            }
            Boolean isForeshadowing = (Boolean) eventData.get("is_foreshadowing");
            Integer chapterRef = eventData.get("chapter_ref") != null
                    ? ((Number) eventData.get("chapter_ref")).intValue()
                    : null;

            // participants를 JSON 문자열로
            String participantsJson = null;
            List<?> participants = (List<?>) eventData.get("participants");
            if (participants != null) {

                try {
                    participantsJson = objectMapper.writeValueAsString(participants);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize participants: {}", e.getMessage());
                }
            }

            // 기존 이벤트 조회 또는 새로 생성 (중복 안전 조회)
            List<Event> existingEvents = eventNeo4jRepository.findAllByProjectIdAndEventId(projectId, eventId);

            Event event;
            if (!existingEvents.isEmpty()) {
                event = existingEvents.get(0);
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
            event.setChapterRef(chapterRef);
            event.setParticipantsJson(participantsJson);
            // 추가 필드 (Neo4j 완전 매핑)
            event.setSequenceOrder(eventData.get("sequence_order") != null
                    ? ((Number) eventData.get("sequence_order")).intValue()
                    : null);
            event.setDocumentId(documentId != null ? documentId.toString() : null);

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

            eventNeo4jRepository.save(event);
            log.debug("Saved event to Neo4j: {} ({})", eventId, narrativeSummary);

            // Neo4j Edge 생성: Event -> Setting (HAPPENED_AT)
            if (locationRef != null && !locationRef.isBlank()) {
                try {
                    eventNeo4jRepository.createHappenedAtEdge(projectId, eventId, locationRef);
                    log.debug("Created HAPPENED_AT edge: {} -> {}", eventId, locationRef);
                } catch (Exception e) {
                    log.warn("Failed to create HAPPENED_AT edge: {} -> {}: {}", eventId, locationRef, e.getMessage());
                }
            }

            // PostgreSQL에도 저장 (AI 서버 호환성, id=project_id, name=narrative_summary)
            // AI에서 events 테이블에 직접 쓰기 위해 필요
            List<?> participantsList = (List<?>) eventData.get("participants");

            String participantsJsonStr = null;
            if (participantsList != null) {
                try {
                    participantsJsonStr = objectMapper.writeValueAsString(participantsList);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize participants for Postgres: {}", e.getMessage());
                }
            }

            // 중복 안전 조회
            List<EventEntity> existingEntities = eventJpaRepository.findAllByProjectAndName(project,
                    narrativeSummary != null ? narrativeSummary : "Untitled Event");
            EventEntity eventEntity;
            if (!existingEntities.isEmpty()) {
                eventEntity = existingEntities.get(0);
            } else {
                eventEntity = EventEntity.builder()
                        .project(project)
                        .eventId(eventId)
                        .name(narrativeSummary != null ? narrativeSummary : "Untitled Event")
                        .documentId(documentId)
                        .build();
            }

            eventEntity.updateDetails(
                    description,
                    eventType,
                    participantsJsonStr,
                    (String) eventData.get("start_time"),
                    (String) eventData.get("end_time"),
                    locationRef,
                    importance != null ? importance.doubleValue() : 5.0,
                    (String) eventData.get("plot_relevance"),
                    (String) eventData.get("cause"),
                    (String) eventData.get("effect"),
                    chapterRef,
                    (Integer) eventData.get("sequence_order"),
                    narrativeSummary,
                    prevEventId);

            // 추가 필드 저장 (AI 콜백 완전 매핑)
            try {
                if (eventData.get("timestamp") != null) {
                    eventEntity.setTimestampJson(objectMapper.writeValueAsString(eventData.get("timestamp")));
                }
                if (eventData.get("changes_made") != null) {
                    eventEntity.setChangesMadeJson(objectMapper.writeValueAsString(eventData.get("changes_made")));
                }
                if (eventData.get("embedding") != null) {
                    eventEntity.setEmbeddingJson(objectMapper.writeValueAsString(eventData.get("embedding")));
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize event extra JSON fields: {}", e.getMessage());
            }

            log.debug("Saving EventEntity: eventId={}, projectId={}", eventEntity.getEventId(),
                    eventEntity.getProject().getId());

            try {
                eventJpaRepository.save(eventEntity);
            } catch (Exception e) {
                log.error("Failed to save EventEntity. EventID: {}, Error: {}", eventId, e.getMessage());
                throw e;
            }

            log.debug("Saved event to PostgreSQL: {} ({})", eventId, narrativeSummary);
        }
    }

    /**
     * 설정(장소) 저장 (Neo4j)
     */
    @SuppressWarnings("unchecked")
    private void saveSettings(Map<String, Object> result, Project project) {
        List<Map<String, Object>> settings = (List<Map<String, Object>>) result.get("settings");
        if (settings == null || settings.isEmpty()) {
            log.debug("No settings to save");
            return;
        }

        String projectId = project.getId().toString();

        for (Map<String, Object> settingData : settings) {
            String settingId = (String) settingData.get("setting_id");
            String name = (String) settingData.get("name");
            String locationType = (String) settingData.get("location_type");
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

            try {
                // 기존 설정 조회 또는 새로 생성 (중복 안전 조회)
                List<Setting> existingSettings = settingNeo4jRepository.findAllByProjectIdAndName(projectId, name);

                Setting setting;
                if (!existingSettings.isEmpty()) {
                    setting = existingSettings.get(0);
                } else {
                    setting = Setting.builder()
                            .projectId(projectId)
                            .settingId(settingId)
                            .name(name)
                            .build();
                }

                setting.setLocationType(locationType != null ? locationType.toUpperCase() : null);
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
                setting.setStaticObjectsJson(staticObjectsJson);
                // 추가 필드 (AI 콜백 완전 매핑)
                setting.setParentLocation((String) settingData.get("parent_location"));
                setting.setFirstMentioned((String) settingData.get("first_mentioned"));

                settingNeo4jRepository.save(setting);
                log.debug("Saved setting to Neo4j: {} ({})", name, locationType);

                // PostgreSQL에도 저장 (AI 서버 호환성, id=project_id, name=name)
                Optional<SettingEntity> existingEntity = settingRepository.findByProjectAndName(project, name);
                SettingEntity settingEntity;
                if (existingEntity.isPresent()) {
                    settingEntity = existingEntity.get();
                } else {
                    settingEntity = SettingEntity.builder()
                            .project(project)
                            .settingId(settingId)
                            .name(name)
                            .build();
                }

                settingEntity.updateDetails(
                        (String) settingData.get("description"),
                        visualPrompt,
                        (String) settingData.get("visual_background"),
                        timeOfDay,
                        lightingDescription,
                        atmosphereKeywords,
                        weatherCondition,
                        (String) settingData.get("art_style"),
                        isPrimary != null ? isPrimary : false,
                        storySignificance,
                        staticObjectsJson);
                // 추가 필드 (AI 콜백 완전 매핑)
                settingEntity.setParentLocation((String) settingData.get("parent_location"));
                settingEntity.setFirstMentioned((String) settingData.get("first_mentioned"));
                settingEntity.setLocationName((String) settingData.get("location_name"));

                settingRepository.save(settingEntity);
                log.debug("Saved setting to PostgreSQL: {} ({})", name, locationType);
            } catch (Exception e) {
                log.error("Failed to save setting {}: {}", name, e.getMessage());
            }

        }
    }

    /**
     * 감정 정보를 캐릭터에 업데이트 (Neo4j)
     */
    @SuppressWarnings("unchecked")
    private void updateEmotions(Map<String, Object> result, String projectId) {
        Map<String, Object> emotionsData = (Map<String, Object>) result.get("emotions");
        if (emotionsData == null) {
            log.debug("No emotions to update");
            return;
        }

        List<Map<String, Object>> neo4jUpdates = (List<Map<String, Object>>) emotionsData.get("neo4j_updates");
        if (neo4jUpdates == null || neo4jUpdates.isEmpty()) {
            log.debug("No neo4j emotion updates");
            return;
        }

        for (Map<String, Object> update : neo4jUpdates) {
            String characterName = (String) update.get("character_name");
            Map<String, Object> propertyUpdates = (Map<String, Object>) update.get("property_updates");

            if (characterName == null || propertyUpdates == null) {
                continue;
            }

            Optional<Character> charOpt = characterRepository.findAllByNameAndProjectId(characterName, projectId)
                    .stream().findFirst();
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

        String jobId = callback.getJobId();
        String characterId = callback.getCharacterId().toString();

        // URL 수정 (minio -> localhost) - 로컬 환경 호환성
        String imageUrl = callback.getImageUrl();
        if (imageUrl != null && imageUrl.contains("minio:9000")) {
            imageUrl = imageUrl.replace("minio:9000", "localhost:9000");
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
     */
    @SuppressWarnings("unchecked")
    private void savePlotIntegration(Map<String, Object> result, Project project, UUID documentId) {
        Map<String, Object> plotData = (Map<String, Object>) result.get("plot_integration");
        if (plotData == null) {
            log.debug("No plot_integration to save");
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

            // Document에도 JSON 저장 (분석 결과 뷰용)
            documentRepository.findById(documentId).ifPresent(doc -> {
                try {
                    doc.setPlotIntegrationJson(objectMapper.writeValueAsString(plotData));
                    documentRepository.save(doc);
                } catch (JsonProcessingException e) {
                    log.error("Failed to save plot integration to Document: {}", e.getMessage());
                }
            });
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
            log.debug("No consistency_report to save");
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

            // Document에도 JSON 저장 (분석 결과 뷰용) - JobId로 Document를 찾기 어려우므로 Job에서 DocumentId를
            // 가져와야 하나,
            // 여기서는 AnalysisJob을 다시 조회하거나 파라미터로 받아야 함.
            // 현재 구조상 saveConsistencyReport는 handleAnalysisCallback(jobId 있음) 내에서 호출됨.
            // handleAnalysisCallback에서 Job을 조회했으므로, Job.documentId를 넘겨주는 것이 좋음.
            // 하지만 메서드 서명이 변경되므로, 일단 JobId로 Job을 다시 조회하여 Document 업데이트
            analysisJobRepository.findByJobId(jobId).ifPresent(job -> {
                documentRepository.findById(job.getDocumentId()).ifPresent(doc -> {
                    try {
                        doc.setConsistencyReportJson(objectMapper.writeValueAsString(reportData));
                        documentRepository.save(doc);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to save consistency report to Document: {}", e.getMessage());
                    }
                });
            });
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
            log.debug("No validation to save");
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

            // Document에도 JSON 저장
            analysisJobRepository.findByJobId(jobId).ifPresent(job -> {
                documentRepository.findById(job.getDocumentId()).ifPresent(doc -> {
                    try {
                        doc.setValidationJson(objectMapper.writeValueAsString(validationData));
                        documentRepository.save(doc);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to save validation result to Document: {}", e.getMessage());
                    }
                });
            });
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
            log.debug("No foreshadowing to save");
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

            try {
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
                log.debug("Saved foreshadowing: {} (confidence: {})", foreshadowId, confidence);
            } catch (Exception e) {
                log.error("Failed to save foreshadowing {}: {}", foreshadowId, e.getMessage());
            }

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

    // Fields moved to class level @RequiredArgsConstructor

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
            saveEvents(tempResult, project, documentId);
        }
        if (callback.getSettings() != null && !callback.getSettings().isEmpty()) {
            java.util.Map<String, Object> tempResult = new java.util.HashMap<>();
            tempResult.put("settings", callback.getSettings());
            saveSettings(tempResult, project);
        }

        // 2-1. 심화 분석 결과 저장 (복선, 플롯, 일관성) - RequiresDeepAnalysis=true일 때
        if (callback.getPlotIntegration() != null) {
            java.util.Map<String, Object> tempResult = new java.util.HashMap<>();
            tempResult.put("plot_integration", callback.getPlotIntegration());
            savePlotIntegration(tempResult, project, documentId);
            saveForeshadowing(tempResult, project);
        }

        if (callback.getConsistencyReport() != null) {
            java.util.Map<String, Object> tempResult = new java.util.HashMap<>();
            tempResult.put("consistency_report", callback.getConsistencyReport());
            logConsistencyReport(tempResult);
            saveConsistencyReport(tempResult, project, callback.getTraceId()); // jobId 대신 traceId 사용 (1차 패스엔 jobId 없음)
        }

        // 3. 문서 상태 업데이트
        document.updateAnalysisStatus(com.stolink.backend.domain.document.entity.Document.AnalysisStatus.COMPLETED);
        documentRepository.save(document);

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
            java.util.List<com.stolink.backend.domain.ai.dto.DocumentAnalysisCallbackDTO.SectionDTO> sections) {
        if (sections == null || sections.isEmpty()) {
            log.debug("No sections to save for document: {}", document.getId());
            return;
        }

        // 기존 Section 삭제 (재분석 시)
        sectionRepository.deleteByDocumentId(document.getId());

        for (com.stolink.backend.domain.ai.dto.DocumentAnalysisCallbackDTO.SectionDTO sectionDTO : sections) {
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
        }
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
                characterRepository.mergeNodes(primaryId, oldId);
                log.info("Merged character: {} -> {}", oldId, primaryId);
            } catch (Exception e) {
                log.warn("Failed to merge character {}: {}", oldId, e.getMessage());
            }
        }

        log.info("Applied character merge: {} <- {} (aliases: {})",
                primaryId, mergedIds, merge.getMergedAliases());
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
