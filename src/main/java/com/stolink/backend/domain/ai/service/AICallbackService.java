package com.stolink.backend.domain.ai.service;

import java.time.format.DateTimeFormatter;
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
import com.stolink.backend.domain.ai.dto.DocumentAnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.GlobalMergeCallbackDTO;
import com.stolink.backend.domain.ai.dto.ImageCallbackDTO;
import com.stolink.backend.domain.ai.dto.callback.CharacterDTO;
import com.stolink.backend.domain.ai.dto.callback.ConsistencyReportDTO;
import com.stolink.backend.domain.ai.dto.callback.EventDTO;
import com.stolink.backend.domain.ai.dto.callback.PlotDTO;
import com.stolink.backend.domain.ai.dto.callback.RelationshipDTO;
import com.stolink.backend.domain.ai.dto.callback.SectionDTO;
import com.stolink.backend.domain.ai.dto.callback.SettingDTO;
import com.stolink.backend.domain.ai.dto.callback.ValidationDTO;
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
import com.stolink.backend.domain.document.repository.SectionRepository;
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
    private final SectionRepository sectionRepository;
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

        // Job에서 Project와 projectId 획득 및 영속성 컨텍스트 재진입
        Project projectProxy = job.getProject();
        Project project = projectRepository.findById(projectProxy.getId())
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectProxy.getId()));
        String projectIdStr = project.getId().toString();

        // 1. 캐릭터 저장 (Neo4j & Postgres) - using effective getter
        saveCharacters(callback.getEffectiveCharacters(), project);

        // 2. 관계 저장 (Neo4j & Postgres) - using effective getter
        // Relationships reference characters by NAME, not ID
        saveRelationships(callback.getEffectiveRelationships(), project);

        // 3. 감정 정보를 캐릭터에 업데이트 (Neo4j)
        Map<String, Object> emotions = callback.getEffectiveEmotions();
        if (emotions != null) {
            updateEmotions(emotions, projectIdStr);
        }

        // 4. 이벤트 저장 (Neo4j & Postgres) - using effective getter
        saveEvents(callback.getEffectiveEvents(), project, job.getDocumentId());

        // 5. 설정(장소) 저장 (Neo4j & Postgres) - using effective getter
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

        // SSE 알림 전송 (프론트엔드 업데이트용)
        sseEmitterService.sendStatus(job.getProject().getId(), new SseEmitterService.AnalysisStatusEvent(
                "COMPLETED", 1, 1, "분석이 완료되었습니다."));

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
     * 캐릭터 저장 (Neo4j & Postgres)
     */
    private void saveCharacters(List<CharacterDTO> characters, Project project) {
        String projectId = project.getId().toString();
        if (characters == null || characters.isEmpty()) {
            log.debug("No characters to save");
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

    private void saveCharacterToPostgres(CharacterDTO charData, Project project) {
        String name = null;
        if (charData.getProfile() != null) {
            name = charData.getProfile().getName();
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

        characterJpaRepository.save(entity);
        log.debug("Saved character to Postgres: {}", name);
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
     * 관계 저장 (Neo4j & Postgres)
     *
     * source/target are CHARACTER NAMES, not IDs.
     * Creates placeholder characters if they don't exist to ensure no relationship data is lost.
     */
    private void saveRelationships(List<RelationshipDTO> relationships, Project project) {
        String projectId = project.getId().toString();
        log.info("Saving {} relationships for project: {}",
            relationships != null ? relationships.size() : 0, projectId);

        if (relationships == null || relationships.isEmpty()) {
            log.debug("No relationships to save");
            return;
        }

        for (RelationshipDTO relData : relationships) {
            String sourceName = relData.getSource();
            String targetName = relData.getTarget();
            String relationType = relData.getRelationType();
            Integer strength = relData.getStrength() != null ? relData.getStrength() : 5;
            String description = relData.getDescription();
            Boolean bidirectional = relData.getBidirectional();

            if (sourceName == null || targetName == null) {
                log.warn("Skipping relationship with missing source/target: {} -> {}", sourceName, targetName);
                continue;
            }

            // --- Neo4j Processing ---
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
                        description,
                        bidirectional != null ? bidirectional : false);
                log.info("Created relationship in Neo4j: {} -[{}]-> {}", sourceName, relationType, targetName);
            } catch (Exception e) {
                log.error("Failed to create relationship in Neo4j: {} -> {}: {}", sourceName, targetName, e.getMessage());
            }

            // --- PostgreSQL Processing ---
            saveRelationshipToPostgres(relData, project);
        }
    }

    private void saveRelationshipToPostgres(RelationshipDTO relData, Project project) {
        String sourceName = relData.getSource();
        String targetName = relData.getTarget();
        String relationType = relData.getRelationType();
        Integer strength = relData.getStrength() != null ? relData.getStrength() : 5;
        String description = relData.getDescription();
        Boolean bidirectional = relData.getBidirectional();

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
        }
    }

    /**
     * 이벤트 저장 (Neo4j & Postgres)
     */
    private void saveEvents(List<EventDTO> events, Project project, UUID jobDocumentId) {
        if (events == null || events.isEmpty()) {
            log.debug("No events to save");
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
            Integer chapterRef = eventData.getChapter();
            Integer sequenceOrder = eventData.getSequenceOrder();
            UUID documentId = null;

            if (eventData.getDocumentId() != null) {
                try {
                    documentId = UUID.fromString(eventData.getDocumentId());
                } catch (IllegalArgumentException e) {
                    documentId = jobDocumentId;
                }
            } else {
                documentId = jobDocumentId;
            }

            // participants를 JSON 문자열로
            String participantsJson = null;
            if (eventData.getParticipants() != null) {
                try {
                    participantsJson = objectMapper.writeValueAsString(eventData.getParticipants());
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize participants: {}", e.getMessage());
                }
            }

            // --- Neo4j ---
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
            event.setSequenceOrder(sequenceOrder);
            event.setDocumentId(documentId != null ? documentId.toString() : null);
            event.setChapter(chapterRef);

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

            // Neo4j Edge 생성: Event -> Setting (HAPPENED_AT)
            if (locationRef != null && !locationRef.isBlank()) {
                try {
                    eventNeo4jRepository.createHappenedAtEdge(projectId, eventId, locationRef);
                    log.debug("Created HAPPENED_AT edge: {} -> {}", eventId, locationRef);
                } catch (Exception e) {
                    log.warn("Failed to create HAPPENED_AT edge: {} -> {}: {}", eventId, locationRef, e.getMessage());
                }
            }

            log.info("Saved event to Neo4j: {} (chapter: {}, seq: {})", eventId, chapterRef, sequenceOrder);

            // --- PostgreSQL ---
            saveEventToPostgres(eventData, project, documentId, participantsJson);
        }
    }

    private void saveEventToPostgres(EventDTO eventData, Project project, UUID documentId, String participantsJsonStr) {
        String eventId = eventData.getEventId();
        String narrativeSummary = eventData.getNarrativeSummary();

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
                eventData.getDescription(),
                eventData.getEventType(),
                participantsJsonStr,
                eventData.getTimestamp() != null ? (String) eventData.getTimestamp().get("start") : null,
                eventData.getTimestamp() != null ? (String) eventData.getTimestamp().get("end") : null,
                eventData.getLocationRef(),
                eventData.getImportance() != null ? eventData.getImportance().doubleValue() : 5.0,
                null, // plot_relevance missing in DTO
                null, // cause missing in DTO
                null, // effect missing in DTO
                eventData.getChapter(),
                eventData.getSequenceOrder(),
                narrativeSummary,
                eventData.getPrevEventId());

        // 추가 필드 저장 (AI 콜백 완전 매핑)
        try {
            if (eventData.getTimestamp() != null) {
                eventEntity.setTimestampJson(objectMapper.writeValueAsString(eventData.getTimestamp()));
            }
            if (eventData.getChangesMade() != null) {
                eventEntity.setChangesMadeJson(objectMapper.writeValueAsString(eventData.getChangesMade()));
            }
            if (eventData.getEmbedding() != null) {
                eventEntity.setEmbeddingJson(objectMapper.writeValueAsString(eventData.getEmbedding()));
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize event extra JSON fields: {}", e.getMessage());
        }

        try {
            eventJpaRepository.save(eventEntity);
            log.debug("Saved event to PostgreSQL: {} ({})", eventId, narrativeSummary);
        } catch (Exception e) {
            log.error("Failed to save EventEntity. EventID: {}, Error: {}", eventId, e.getMessage());
        }
    }

    /**
     * 설정(장소) 저장 (Neo4j & Postgres)
     */
    private void saveSettings(List<SettingDTO> settings, Project project) {
        if (settings == null || settings.isEmpty()) {
            log.debug("No settings to save");
            return;
        }

        String projectId = project.getId().toString();

        for (SettingDTO settingData : settings) {
            String settingId = settingData.getSettingId();
            String name = settingData.getName();
            String locationType = settingData.getLocationType();
            String visualPrompt = settingData.getVisualBackground();
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

            // --- Neo4j ---
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

            setting.setParentLocation(settingData.getParentLocation());
            setting.setFirstMentioned(settingData.getFirstMentioned());

            try {
                if (settingData.getEmbedding() != null) {
                    setting.setEmbeddingJson(objectMapper.writeValueAsString(settingData.getEmbedding()));
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize setting embedding: {}", e.getMessage());
            }

            settingNeo4jRepository.save(setting);
            log.info("Saved setting to Neo4j: {} (type: {}, primary: {})", name, locationType, isPrimary);

            // --- PostgreSQL ---
            saveSettingToPostgres(settingData, project, staticObjectsJson);
        }
    }

    private void saveSettingToPostgres(SettingDTO settingData, Project project, String staticObjectsJson) {
        String name = settingData.getName();
        Optional<SettingEntity> existingEntity = settingRepository.findByProjectAndName(project, name);
        SettingEntity settingEntity;
        if (existingEntity.isPresent()) {
            settingEntity = existingEntity.get();
        } else {
            settingEntity = SettingEntity.builder()
                    .project(project)
                    .settingId(settingData.getSettingId())
                    .name(name)
                    .build();
        }

        settingEntity.updateDetails(
                settingData.getDescription(),
                settingData.getVisualBackground(),
                settingData.getVisualBackground(),
                settingData.getTimeOfDay(),
                settingData.getLighting(),
                settingData.getAtmosphere(),
                settingData.getWeather(),
                settingData.getArtStyle(),
                settingData.getIsPrimary() != null ? settingData.getIsPrimary() : false,
                settingData.getSignificance(),
                staticObjectsJson);

        settingEntity.setParentLocation(settingData.getParentLocation());
        settingEntity.setFirstMentioned(settingData.getFirstMentioned());
        settingEntity.setLocationName(settingData.getLocationName());

        try {
            settingRepository.save(settingEntity);
            log.debug("Saved setting to PostgreSQL: {}", name);
        } catch (Exception e) {
            log.error("Failed to save setting {}: {}", name, e.getMessage());
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

        if (callback.getCharacterId() == null) {
            log.warn("Received image callback without characterId for job: {}", jobId);
            return;
        }
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
            // Fall back to summary field
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

            // Document에도 JSON 저장
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
    private void saveConsistencyReport(ConsistencyReportDTO reportData, Project project, String jobId) {
        if (reportData == null) {
            log.debug("No consistency_report to save");
            return;
        }

        try {
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
    private void saveValidationResult(ValidationDTO validationData, Project project, String jobId) {
        if (validationData == null) {
            log.debug("No validation to save");
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
     * 복선 저장 (PostgreSQL)
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

    /**
     * 글로벌 병합 결과 콜백 처리
     */
    @Transactional
    public void handleGlobalMergeCallback(GlobalMergeCallbackDTO callback) {
        log.info("Processing global merge callback for project: {}, status: {}",
                callback.getProjectId(), callback.getStatus());

        if (!callback.isSuccess()) {
             log.error("Global merge failed: {}", callback.getError());
             return;
        }

        List<GlobalMergeCallbackDTO.CharacterMergeDTO> merges = callback.getCharacterMerges();
        if (merges != null) {
            for (GlobalMergeCallbackDTO.CharacterMergeDTO merge : merges) {
                String primaryId = merge.getPrimaryId();
                List<String> mergedIds = merge.getMergedIds();

                if (primaryId == null || mergedIds == null) continue;

                for (String mergedId : mergedIds) {
                    if (mergedId.equals(primaryId)) continue;
                    try {
                        characterRepository.mergeNodes(primaryId, mergedId);
                        log.info("Merged character {} into {}", mergedId, primaryId);
                    } catch (Exception e) {
                        log.error("Failed to merge {} into {}: {}", mergedId, primaryId, e.getMessage());
                    }
                }
            }
        }

        // 일관성 리포트나 기타 메타데이터 저장 로직 추가 가능
        if (callback.getConsistencyReport() != null) {
             log.info("Global consistency report received. Score: {}", callback.getConsistencyReport().get("overall_score"));
             // TODO: Save global consistency report
        }
    }

    /**
     * 문서 분석 결과 콜백 처리 (1차 Pass)
     */
    @Transactional
    public void handleDocumentAnalysisCallback(DocumentAnalysisCallbackDTO callback) {
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
            saveCharacters(callback.getCharacters(), project);
        }
        if (callback.getEvents() != null && !callback.getEvents().isEmpty()) {
            saveEvents(callback.getEvents(), project, documentId);
        }

        // 3. 임시 관계 저장 (임시 저장용, 나중에 글로벌 병합 시 정제됨)
        // 1차 패스에서는 관계 추출이 제한적일 수 있음.

        log.info("Document analysis callback processed: {}", callback.getDocumentId());
    }

    private void saveSections(com.stolink.backend.domain.document.entity.Document document, List<SectionDTO> sections) {
        if (sections == null || sections.isEmpty()) return;

        sectionRepository.deleteByDocumentId(document.getId());

        for (SectionDTO secDto : sections) {
            float[] embedding = null;
            if (secDto.getEmbedding() != null) {
                embedding = new float[secDto.getEmbedding().size()];
                for (int i = 0; i < secDto.getEmbedding().size(); i++) {
                    embedding[i] = secDto.getEmbedding().get(i).floatValue();
                }
            }

            com.stolink.backend.domain.document.entity.Section section = com.stolink.backend.domain.document.entity.Section.builder()
                    .document(document)
                    .sequenceOrder(secDto.getSequenceOrder())
                    .navTitle(secDto.getNavTitle())
                    .content(secDto.getContent())
                    .embedding(embedding)
                    .build();
            sectionRepository.save(section);
        }
    }
}
