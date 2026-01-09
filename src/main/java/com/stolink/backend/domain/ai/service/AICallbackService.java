package com.stolink.backend.domain.ai.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
import com.stolink.backend.domain.character.repository.ImageGenerationTaskRepository;
import com.stolink.backend.domain.character.repository.RelationshipRepository;
import com.stolink.backend.domain.consistency.entity.ConsistencyReport;
import com.stolink.backend.domain.consistency.repository.ConsistencyReportRepository;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.document.repository.SectionRepository;
import com.stolink.backend.domain.event.entity.EventEntity;
// Neo4j Event node removed - using PostgreSQL only
import com.stolink.backend.domain.event.repository.EventJpaRepository;
import com.stolink.backend.domain.event.service.EventDeduplicationService;
// EventNeo4jRepository removed - using PostgreSQL only
import com.stolink.backend.domain.foreshadowing.entity.Foreshadowing;
import com.stolink.backend.domain.foreshadowing.repository.ForeshadowingRepository;
import com.stolink.backend.domain.plot.entity.PlotIntegration;
import com.stolink.backend.domain.plot.repository.PlotIntegrationRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.setting.entity.SettingEntity;
// Neo4j Setting node and SettingNeo4jRepository removed - using PostgreSQL only
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

    // CharacterRepository (Neo4j) removed - using PostgreSQL only
    private final com.stolink.backend.domain.character.repository.CharacterJpaRepository characterJpaRepository;
    private final com.stolink.backend.domain.character.repository.CharacterRepository characterRepository; // Neo4j
    private final DocumentRepository documentRepository;
    // EventNeo4jRepository removed - using PostgreSQL only
    private final EventJpaRepository eventJpaRepository;
    private final EventDeduplicationService eventDeduplicationService;
    private final RelationshipRepository relationshipRepository;

    // SettingNeo4jRepository removed - using PostgreSQL only
    private final SettingRepository settingRepository;
    private final ImageGenerationTaskRepository imageGenerationTaskRepository;

    private final AnalysisJobRepository analysisJobRepository;
    private final SectionRepository sectionRepository;
    private final PlotIntegrationRepository plotIntegrationRepository;
    private final ConsistencyReportRepository consistencyReportRepository;
    private final ValidationResultRepository validationResultRepository;
    private final ForeshadowingRepository foreshadowingRepository;
    private final ProjectRepository projectRepository;

    private final CallbackLogRepository callbackLogRepository;
    private final ObjectMapper objectMapper;
    private final SseEmitterService sseEmitterService;
    private final TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.ai.callback-base-url}")
    private String callbackBaseUrl;

    /**
     * 분석 결과 콜백 처리 (Multi-Agent 파이프라인 결과)
     */

    public void handleAnalysisCallback(AnalysisCallbackDTO callback) {
        log.info("Processing analysis callback for job: {}, status: {}",
                callback.getJobId(), callback.getStatus());

        // Clear JPA L1 cache to ensure fresh reads of AI-written data
        entityManager.clear();

        // Save raw callback data to file
        saveCallbackToJsonFile("analysis", callback.getJobId(), callback);

        // Idempotent check: skip if already processed
        if (callbackLogRepository.existsByJobId(callback.getJobId())) {
            log.warn("Duplicate callback ignored: {}", callback.getJobId());
            return;
        }

        // Job 조회
        AnalysisJob job = analysisJobRepository.findByJobId(callback.getJobId()).orElse(null);

        // [TEMPORARY] Dummy Data Injection Logic
        if (job == null && callback.getJobId().startsWith("dummy")) {
            log.warn("Job not found, but detecting dummy data injection. Creating context for insertion...");
            try {
                UUID tempProjectId = null;
                String[] jobIdParts = callback.getJobId().split("::");
                if (jobIdParts.length >= 2) {
                    try {
                        tempProjectId = UUID.fromString(jobIdParts[1]);
                    } catch (IllegalArgumentException e) {
                        tempProjectId = UUID.fromString("e2a08b38-9049-4647-b9f0-1cac7792a2d7");
                    }
                } else {
                    tempProjectId = UUID.fromString("e2a08b38-9049-4647-b9f0-1cac7792a2d7");
                }

                final UUID finalTargetProjectId = tempProjectId;
                log.info("Dummy injection target project: {}", finalTargetProjectId);
                Project project = projectRepository.findById(finalTargetProjectId)
                        .orElseThrow(() -> new RuntimeException("Target project not found: " + finalTargetProjectId));

                // Find or Create a Placeholder Document
                Document doc = documentRepository.findTextDocumentsByProjectId(finalTargetProjectId).stream()
                        .findFirst()
                        .orElseGet(() -> {
                            Document newDoc = Document.builder()
                                    .project(project)
                                    .title("Dummy Data Container")
                                    .type(Document.DocumentType.TEXT)
                                    .order(0)
                                    .content("Content for dummy data holder")
                                    .build();
                            return documentRepository.save(newDoc);
                        });

                job = AnalysisJob.builder()
                        .jobId(callback.getJobId())
                        .project(project)
                        .documentId(doc.getId())
                        .status(AnalysisJob.JobStatus.PROCESSING)
                        .startedAt(java.time.LocalDateTime.now())
                        .build();
                analysisJobRepository.save(job);
                log.info("Created temporary job context: {}", job.getJobId());

                // Cleanup existing characters and relationships for this project
                transactionTemplate.execute(status -> {
                    relationshipRepository.deleteByProjectId(finalTargetProjectId);
                    characterJpaRepository.deleteAllByProject(project);
                    characterRepository.deleteByProjectId(finalTargetProjectId.toString());
                    log.info("Cleaned up existing data for project: {}", finalTargetProjectId);
                    return null;
                });
            } catch (Exception e) {
                log.error("Failed to create dummy context or cleanup: {}", e.getMessage());
            }
        }

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
        transactionTemplate.execute(status -> {
            // Re-fetch job to ensure freshness and attachment
            analysisJobRepository.findById(callback.getJobId()).ifPresent(j -> {
                j.markAsCompleted(processingTimeMs);
                analysisJobRepository.save(j);
            });
            return null;
        });

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

        // SSE 알림 전송 (프론트엔드 업데이트용 - 실시간 진행률)
        sendProgressUpdate(project.getId());

        log.info("Analysis callback processed successfully for job: {}", callback.getJobId());
    }

    // Document Status Update Helper
    private void updateDocumentStatus(UUID documentId, Document.AnalysisStatus status) {
        if (documentId == null)
            return;

        try {
            transactionTemplate.execute(txStatus -> {
                documentRepository.findById(documentId).ifPresent(doc -> {
                    doc.updateAnalysisStatus(status);
                    documentRepository.save(doc);
                    log.info("Updated Document {} status to {}", documentId, status);
                });
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to update document status: {}", e.getMessage());
        }
    }

    /**
     * 캐릭터 저장 (PostgreSQL only - Neo4j는 AI Backend에서 처리)
     */
    private void saveCharacters(List<CharacterDTO> characters, Project project) {
        if (characters == null || characters.isEmpty()) {
            log.debug("No characters to save");
            return;
        }

        for (CharacterDTO charData : characters) {
            try {
                // 1. Save to Postgres
                saveCharacterToPostgres(charData, project);

                // 2. Save to Neo4j [ADDED FOR DUMMY DATA]
                saveCharacterToNeo4j(charData, project);
            } catch (Exception e) {
                log.error("Failed to save character: {}", e.getMessage());
            }
        }
    }

    private void saveCharacterToNeo4j(CharacterDTO charData, Project project) {
        try {
            String name = charData.getProfile() != null ? charData.getProfile().getName() : "Unknown";
            String projectId = project.getId().toString();

            // Find existing by Name and Project as ID (UUID) might mismatch
            List<com.stolink.backend.domain.character.node.Character> existing = characterRepository
                    .findAllByNameAndProjectId(name, projectId);

            com.stolink.backend.domain.character.node.Character neoChar;
            if (!existing.isEmpty()) {
                neoChar = existing.get(0);
                log.debug("Updating existing Neo4j character: {}", name);
            } else {
                neoChar = new com.stolink.backend.domain.character.node.Character();
                neoChar.setProjectId(projectId);
                neoChar.setName(name);
                log.debug("Creating new Neo4j character: {}", name);
            }

            // Fill data
            neoChar.setCharacterId(charData.getId());
            neoChar.setRole(charData.getRole());
            neoChar.setStatus(charData.getStatus());
            neoChar.setImageUrl(charData.getImageUrl());

            if (charData.getProfile() != null) {
                CharacterDTO.ProfileDTO p = charData.getProfile();
                neoChar.setAge(p.getAge());
                neoChar.setGender(p.getGender());
                neoChar.setRace(p.getRace());
                neoChar.setMbti(p.getMbti());
                neoChar.setBackstory(p.getBackstory());
                if (p.getFaction() != null)
                    neoChar.setFaction(p.getFaction().getName());
                neoChar.setProfileJson(objectMapper.writeValueAsString(p));
            }

            if (charData.getAliases() != null)
                neoChar.setAliasesJson(objectMapper.writeValueAsString(charData.getAliases()));
            if (charData.getAppearance() != null)
                neoChar.setAppearanceJson(objectMapper.writeValueAsString(charData.getAppearance()));
            if (charData.getRelations() != null)
                neoChar.setRelationsJson(objectMapper.writeValueAsString(charData.getRelations()));
            if (charData.getCurrentMood() != null)
                neoChar.setCurrentMoodJson(objectMapper.writeValueAsString(charData.getCurrentMood()));

            characterRepository.save(neoChar);
            log.debug("Saved Neo4j character: {}", name);

        } catch (Exception e) {
            log.error("Failed to save character to Neo4j: {}", e.getMessage());
        }
    }

    private void saveCharacterToPostgres(CharacterDTO charData, Project project) {
        String name = charData.getProfile() != null ? charData.getProfile().getName() : "Unknown";

        // 중복 캐릭터가 있어도 첫 번째 결과만 사용 (안전한 조회)
        java.util.List<com.stolink.backend.domain.character.entity.CharacterEntity> existingEntities = characterJpaRepository
                .findAllByProjectAndName(project, name);
        com.stolink.backend.domain.character.entity.CharacterEntity entity;
        if (!existingEntities.isEmpty()) {
            entity = existingEntities.get(0);
            log.debug("Updating existing PostgreSQL character: {}", name);
        } else {
            entity = com.stolink.backend.domain.character.entity.CharacterEntity.builder()
                    .id(UUID.randomUUID())
                    .project(project)
                    .name(name)
                    .build();
            log.debug("Creating new PostgreSQL character: {}", name);
        }

        // Basic fields
        entity.setCharacterId(charData.getId());
        entity.setRole(charData.getRole());
        entity.setStatus(charData.getStatus());
        entity.setImageUrl(charData.getImageUrl());

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

            // Image URL mapping
            if (charData.getImageUrl() != null) {
                entity.setImageUrl(charData.getImageUrl());
            }

            // Appearance
            if (charData.getAppearance() != null) {
                String appearanceJson = objectMapper.writeValueAsString(charData.getAppearance());
                entity.setAppearanceJson(appearanceJson);
                entity.setVisualJson(appearanceJson); // legacy fallback
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

    // updateCharacterJsonFields method removed - was Neo4j-specific
    // Character JSON field updates now handled by AI Backend

    /**
     * 관계 저장 (Neo4j & Postgres)
     *
     * source/target are CHARACTER NAMES, not IDs.
     * Creates placeholder characters if they don't exist to ensure no relationship
     * data is lost.
     */
    private void saveRelationships(List<RelationshipDTO> relationships, Project project) {
        log.info("Saving {} relationships for project: {}",
                relationships != null ? relationships.size() : 0, project.getId());

        if (relationships == null || relationships.isEmpty()) {
            log.debug("No relationships to save");
            return;
        }

        for (RelationshipDTO relData : relationships) {
            if (relData.getSource() == null || relData.getTarget() == null) {
                log.warn("Skipping relationship with missing source/target: {} -> {}",
                        relData.getSource(), relData.getTarget());
                continue;
            }

            // --- PostgreSQL Processing ---
            saveRelationshipToPostgres(relData, project);

            // --- Neo4j Processing [ADDED FOR DUMMY DATA] ---
            saveRelationshipToNeo4j(relData, project);
        }
    }

    private void saveRelationshipToNeo4j(RelationshipDTO relData, Project project) {
        // Neo4j relationships use IDs, but DTO might provide Names.
        // We need to resolve Name -> ID for Neo4j creation if IDs are missing.
        // Or better: ensure Characters are saved first (they are).
        // Then find the Character Nodes by (ProjectId, Name).

        String sourceName = relData.getSource();
        String targetName = relData.getTarget();

        // 1. Find Source Node
        com.stolink.backend.domain.character.node.Character sourceNode = findCharacterNodeByName(
                project.getId().toString(), sourceName);
        // 2. Find Target Node
        com.stolink.backend.domain.character.node.Character targetNode = findCharacterNodeByName(
                project.getId().toString(), targetName);

        if (sourceNode != null && targetNode != null) {
            characterRepository.createRelationship(
                    sourceNode.getId(),
                    targetNode.getId(),
                    relData.getRelationTypes(),
                    relData.getStrength() != null ? relData.getStrength() : 5,
                    relData.getDescription(),
                    relData.getBidirectional() != null ? relData.getBidirectional() : false);
            log.debug("Saved Neo4j relationship: {} -> {}", sourceName, targetName);
        } else {
            log.warn("Could not find nodes for relationship: {} -> {}", sourceName, targetName);
        }
    }

    private com.stolink.backend.domain.character.node.Character findCharacterNodeByName(String projectId, String name) {
        // Return the first match if multiple exist to avoid "Expected single result"
        // errors.
        List<com.stolink.backend.domain.character.node.Character> matches = characterRepository
                .findAllByNameAndProjectId(name, projectId);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void saveRelationshipToPostgres(RelationshipDTO relData, Project project) {
        String sourceName = relData.getSource();
        String targetName = relData.getTarget();
        List<String> relationTypes = relData.getRelationTypes(); // now List
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
            String typesJson = "[]";
            try {
                typesJson = objectMapper.writeValueAsString(relationTypes != null ? relationTypes : new ArrayList<>());
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize relation types: {}", e.getMessage());
            }

            RelationshipEntity relEntity = RelationshipEntity.builder()
                    .project(project)
                    .sourceCharacter(sourceEntity)
                    .targetCharacter(targetEntity)
                    .sourceName(sourceName)
                    .targetName(targetName)
                    .relationTypesJson(typesJson) // Updated builder field
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
     * 이벤트 저장 (PostgreSQL only - Neo4j는 AI Backend에서 처리)
     */
    private void saveEvents(List<EventDTO> events, Project project, UUID jobDocumentId) {
        if (events == null || events.isEmpty()) {
            log.debug("No events to save");
            return;
        }

        // Group by Chapter for Batch Processing
        Map<Integer, List<EventDTO>> eventsByChapter = events.stream()
                .filter(e -> e.getChapter() != null)
                .collect(Collectors.groupingBy(EventDTO::getChapter));

        // Handle events with no chapter
        List<EventDTO> noChapterEvents = events.stream()
                .filter(e -> e.getChapter() == null)
                .toList();

        // 1. Process Batch (By Chapter)
        for (Map.Entry<Integer, List<EventDTO>> entry : eventsByChapter.entrySet()) {
            Integer chapter = entry.getKey();
            List<EventDTO> chapterEvents = entry.getValue();

            // Batch Fetch Candidates
            List<EventEntity> candidates = eventJpaRepository.findAllByProjectAndChapter(project, chapter);

            for (EventDTO eventData : chapterEvents) {
                processEventWithCandidates(eventData, project, jobDocumentId, candidates);
            }
        }

        // 2. Process Remainder (No chapter -> no batch candidates)
        for (EventDTO eventData : noChapterEvents) {
            processEventWithCandidates(eventData, project, jobDocumentId, null);
        }
    }

    private void processEventWithCandidates(EventDTO eventData, Project project, UUID jobDocumentId,
            List<EventEntity> candidates) {
        UUID documentId = jobDocumentId;
        if (eventData.getDocumentId() != null) {
            try {
                documentId = UUID.fromString(eventData.getDocumentId());
            } catch (IllegalArgumentException e) {
                documentId = jobDocumentId;
            }
        }

        String participantsJson = null;
        if (eventData.getParticipants() != null) {
            try {
                participantsJson = objectMapper.writeValueAsString(eventData.getParticipants());
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize participants: {}", e.getMessage());
            }
        }

        saveEventToPostgres(eventData, project, documentId, participantsJson, candidates);
    }

    private void saveEventToPostgres(EventDTO eventData, Project project, UUID documentId, String participantsJsonStr,
            List<EventEntity> candidates) {
        String eventId = eventData.getEventId();
        String narrativeSummary = eventData.getNarrativeSummary();

        // Hybrid Deduplication
        Optional<EventEntity> duplicateCandidate;

        if (candidates != null) {
            duplicateCandidate = eventDeduplicationService.findDuplicateEventInCandidates(candidates, eventData);
        } else {
            duplicateCandidate = eventDeduplicationService.findDuplicateEvent(project, eventData);
        }

        EventEntity eventEntity;

        if (duplicateCandidate.isPresent()) {
            eventEntity = duplicateCandidate.get();
            log.info("Duplicate event found: {}", eventEntity.getEventId());
        } else {
            // Fallback for backward compatibility (name match)
            List<EventEntity> existingByName = eventJpaRepository.findAllByProjectAndName(project,
                    narrativeSummary != null ? narrativeSummary : "Untitled Event");
            if (!existingByName.isEmpty()) {
                eventEntity = existingByName.get(0);
                log.info("Event matched by name: {}", narrativeSummary);
            } else {
                eventEntity = EventEntity.builder()
                        .project(project)
                        .eventId(eventId)
                        .name(narrativeSummary != null ? narrativeSummary : "Untitled Event")
                        .documentId(documentId)
                        .build();
            }
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

        // 추가 필드 저장
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
     */
    private void saveSettings(List<SettingDTO> settings, Project project) {
        if (settings == null || settings.isEmpty()) {
            log.debug("No settings to save");
            return;
        }

        // Batch fetch existing settings
        Set<String> names = settings.stream()
                .map(SettingDTO::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());

        Map<String, SettingEntity> existingMap = new HashMap<>();
        if (!names.isEmpty()) {
            existingMap = settingRepository.findByProjectAndNameIn(project, names)
                    .stream()
                    .collect(Collectors.toMap(SettingEntity::getName, s -> s, (s1, s2) -> s1));
        }

        for (SettingDTO settingData : settings) {
            String settingId = settingData.getSettingId();
            String name = settingData.getName();

            // name이 null이면 저장할 수 없음 (SettingEntity.name은 NOT NULL)
            if (name == null || name.isBlank()) {
                log.warn("Skipping setting with null/blank name. settingId: {}", settingId);
                continue;
            }

            // static_objects를 JSON 문자열로
            String staticObjectsJson = null;
            if (settingData.getNotableFeatures() != null) {
                try {
                    staticObjectsJson = objectMapper.writeValueAsString(settingData.getNotableFeatures());
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize static_objects: {}", e.getMessage());
                }
            }
            saveSettingToPostgres(settingData, project, staticObjectsJson, existingMap.get(name));
        }
    }

    private void saveSettingToPostgres(SettingDTO settingData, Project project, String staticObjectsJson,
            SettingEntity preFetchedEntity) {
        String name = settingData.getName();

        SettingEntity settingEntity;
        if (preFetchedEntity != null) {
            settingEntity = preFetchedEntity;
        } else {
            // Fallback lookup if not in map (should not happen if batch worked, but for
            // safety)
            // or if it was not in batch because it's new
            // Wait, if it's new, preFetchedEntity is null.
            Optional<SettingEntity> existing = settingRepository.findByProjectAndName(project, name);
            if (existing.isPresent()) {
                settingEntity = existing.get();
            } else {
                settingEntity = SettingEntity.builder()
                        .project(project)
                        .settingId(settingData.getSettingId())
                        .name(name)
                        .build();
            }
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
     * 감정 정보를 캐릭터에 업데이트 (Neo4j 제거됨 - AI Backend에서 처리)
     */
    private void updateEmotions(Map<String, Object> result, String projectId) {
        // Neo4j 저장 로직 제거됨 - AI Backend에서 처리
        log.debug("updateEmotions skipped - Neo4j handling moved to AI Backend");
    }

    /**
     * 일관성 보고서 로깅
     */
    private void logConsistencyReport(ConsistencyReportDTO consistencyReport) {
        if (consistencyReport != null) {
            Integer score = consistencyReport.getEffectiveScore();
            Boolean requiresReextraction = consistencyReport.getRequiresReExtraction();
            Integer highAnalysis = consistencyReport.getHighSeverity();

            // Legacy/Fallback mapping if needed, or just log new fields
            log.info(
                    "Consistency report - score: {}, re-extract: {}, high: {}, medium: {}, auto-fix: {}, human-review: {}",
                    score, requiresReextraction,
                    consistencyReport.getHighSeverity(), consistencyReport.getMediumSeverity(),
                    consistencyReport.getAutoFixable(), consistencyReport.getRequiresHumanReview());
        }
    }

    /**
     * 이미지 생성 결과 콜백 처리
     */
    @Transactional
    public void handleImageCallback(ImageCallbackDTO callback) {
        log.info("Processing image callback for job: {}, character: {}",
                callback.getJobId(), callback.getCharacterId());

        // Save raw callback data to file
        saveCallbackToJsonFile("image", callback.getJobId(), callback);

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

        // 1. Character 엔티티 업데이트 (PostgreSQL)
        final String finalImageUrl = imageUrl;
        characterJpaRepository.findByCharacterId(characterId).ifPresent(characterEntity -> {
            characterEntity.setImageUrl(finalImageUrl);
            characterJpaRepository.save(characterEntity);
            log.info("Updated PostgreSQL character {} with image URL: {}", characterId, finalImageUrl);

            // Setting Prompt Persistence (Side Effect)
            // Re-fetch task to get latest state including settingId
            imageGenerationTaskRepository.findById(jobId).ifPresent(task -> {
                if (task.getSettingId() != null) {
                    updateSettingWithPrompts(task);
                }
            });
        });

        // 2. Neo4j Character 업데이트 (프론트엔드에서 조회하는 소스)
        try {
            UUID charUuid = callback.getCharacterId();
            characterRepository.updateImageUrl(charUuid.toString(), finalImageUrl);
            log.info("Updated Neo4j character {} with image URL: {}", charUuid, finalImageUrl);
        } catch (Exception e) {
            log.warn("Failed to update Neo4j character image URL: {}", e.getMessage());
        }

        // 3. ImageGenerationTask 상태 업데이트 (COMPLETED)
        imageGenerationTaskRepository.findById(jobId).ifPresent(task -> {
            task.setImageUrl(finalImageUrl);
            task.setStatus(ImageGenerationTask.TaskStatus.COMPLETED);
            imageGenerationTaskRepository.save(task);
            log.info("Updated ImageGenerationTask {} to COMPLETED", jobId);
        });
    }

    private void updateSettingWithPrompts(ImageGenerationTask task) {
        if (task.getSettingId() == null)
            return;

        settingRepository.findById(task.getSettingId()).ifPresent(setting -> {
            boolean updated = false;

            if (task.getVisualBackground() != null && !task.getVisualBackground().isBlank()) {
                setting.setVisualBackground(task.getVisualBackground());
                updated = true;
            }
            if (task.getAtmosphere() != null && !task.getAtmosphere().isBlank()) {
                setting.setAtmosphereKeywords(task.getAtmosphere());
                updated = true;
            }
            if (task.getLighting() != null && !task.getLighting().isBlank()) {
                setting.setLightingDescription(task.getLighting());
                updated = true;
            }
            if (task.getTimeOfDay() != null && !task.getTimeOfDay().isBlank()) {
                setting.setTimeOfDay(task.getTimeOfDay());
                updated = true;
            }
            if (task.getArtStyle() != null && !task.getArtStyle().isBlank()) {
                setting.setArtStyle(task.getArtStyle());
                updated = true;
            }

            if (updated) {
                settingRepository.save(setting);
                log.info("Updated Setting {} with prompts from ImageGenerationTask {}", setting.getId(),
                        task.getJobId());
            }
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
                    .highSeverityCount(reportData.getHighSeverity())
                    .mediumSeverityCount(reportData.getMediumSeverity())
                    .autoFixableCount(reportData.getAutoFixable())
                    .requiresHumanReviewCount(reportData.getRequiresHumanReview())
                    .requiresReextraction(reportData.getRequiresReExtraction() != null
                            ? reportData.getRequiresReExtraction()
                            : false)
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

        // Save raw callback data to file
        saveCallbackToJsonFile("global_merge", callback.getProjectId(), callback);

        if (!callback.isSuccess()) {
            log.error("Global merge failed: {}", callback.getError());
            return;
        }

        List<GlobalMergeCallbackDTO.CharacterMergeDTO> merges = callback.getCharacterMerges();
        if (merges != null) {
            for (GlobalMergeCallbackDTO.CharacterMergeDTO merge : merges) {
                String primaryId = merge.getPrimaryId();
                List<String> mergedIds = merge.getMergedIds();

                if (primaryId == null || mergedIds == null)
                    continue;

                for (String mergedId : mergedIds) {
                    if (mergedId.equals(primaryId))
                        continue;
                    // Neo4j mergeNodes 제거됨 - AI Backend에서 처리
                    log.info("Character merge skipped (Neo4j removed): {} into {}", mergedId, primaryId);
                }
            }
        }

        // 일관성 리포트나 기타 메타데이터 저장 로직 추가 가능
        if (callback.getConsistencyReport() != null) {
            log.info("Global consistency report received. Score: {}",
                    callback.getConsistencyReport().get("overall_score"));
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

        // Save raw callback data to file
        saveCallbackToJsonFile("doc_analysis", callback.getDocumentId(), callback);

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

        // SSE 알림 전송 (실시간 진행률)
        sendProgressUpdate(project.getId());

        log.info("Document analysis callback processed: {}", callback.getDocumentId());
    }

    private void saveSections(com.stolink.backend.domain.document.entity.Document document, List<SectionDTO> sections) {
        if (sections == null || sections.isEmpty())
            return;

        sectionRepository.deleteByDocumentId(document.getId());

        for (SectionDTO secDto : sections) {
            float[] embedding = null;
            if (secDto.getEmbedding() != null) {
                embedding = new float[secDto.getEmbedding().size()];
                for (int i = 0; i < secDto.getEmbedding().size(); i++) {
                    embedding[i] = secDto.getEmbedding().get(i).floatValue();
                }
            }

            com.stolink.backend.domain.document.entity.Section section = com.stolink.backend.domain.document.entity.Section
                    .builder()
                    .document(document)
                    .sequenceOrder(secDto.getSequenceOrder())
                    .navTitle(secDto.getNavTitle())
                    .content(secDto.getContent())
                    .embedding(embedding)
                    .build();
            sectionRepository.save(section);
        }
    }

    /**
     * AI 콜백 데이터를 로컬 JSON 파일로 저장 (디버깅용)
     */
    private void saveCallbackToJsonFile(String type, String id, Object data) {
        // Async execution to avoid I/O blocking
        CompletableFuture.runAsync(() -> {
            try {
                String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                        .format(java.time.LocalDateTime.now());
                // Sanitize ID for filename
                String safeId = id != null ? id.replaceAll("[^a-zA-Z0-9-_]", "_") : "unknown";
                String fileName = String.format("logs/ai-callbacks/%s_%s_%s.json", type, safeId, timestamp);

                java.io.File file = new java.io.File(fileName);
                file.getParentFile().mkdirs();

                objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
                log.info("Saved callback data to file (Async): {}", fileName);
            } catch (Exception e) {
                log.error("Failed to save callback data to file: {}", e.getMessage());
            }
        });
    }

    // ===================================
    // SSE Progress Update Helper
    // ===================================
    private void sendProgressUpdate(UUID projectId) {
        if (projectId == null)
            return;

        try {
            long total = documentRepository.countTextDocumentsByProjectId(projectId);
            long completed = documentRepository.countByProjectIdAndTypeTextAndAnalysisStatus(
                    projectId,
                    com.stolink.backend.domain.document.entity.Document.AnalysisStatus.COMPLETED);

            String status = (total > 0 && total <= completed) ? "COMPLETED" : "ANALYZING";
            String message = (total > 0 && total <= completed)
                    ? "분석이 완료되었습니다."
                    : String.format("분석 진행 중: %d/%d", completed, total);

            sseEmitterService.sendStatus(projectId, new SseEmitterService.AnalysisStatusEvent(
                    status, (int) completed, (int) total, message));
            log.debug("Sent SSE progress update for project {}: {}/{}", projectId, completed, total);
        } catch (Exception e) {
            log.warn("Failed to send SSE progress update: {}", e.getMessage());
        }
    }
}
