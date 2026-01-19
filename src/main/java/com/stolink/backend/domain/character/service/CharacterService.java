package com.stolink.backend.domain.character.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.ai.dto.ImageGenerationTaskDTO;
import com.stolink.backend.domain.ai.service.ImageServerHealthChecker;
import com.stolink.backend.domain.ai.service.RabbitMQProducerService;
import com.stolink.backend.domain.character.event.ImageGenerationRequestedEvent;
import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.relationship.CharacterRelationship;
import com.stolink.backend.domain.character.repository.CharacterRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final RabbitMQProducerService producerService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.stolink.backend.domain.document.repository.DocumentRepository documentRepository;
    private final com.stolink.backend.domain.character.repository.ImageGenerationTaskRepository imageGenerationTaskRepository;
    private final org.neo4j.driver.Driver driver;
    private final jakarta.persistence.EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final ImageServerHealthChecker imageServerHealthChecker;

    @Value("${app.ai.callback-base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    public List<Character> getCharacters(UUID userId, UUID projectId) {
        User user = getUserOrThrow(userId);
        Project project = getProjectOrThrow(projectId, user);

        return characterRepository.findByProjectId(project.getId().toString());
    }

    public List<Character> getAllCharacters() {
        return characterRepository.findAll();
    }

    // @Transactional 제거: Neo4j만 사용하므로 PostgreSQL 커넥션 불필요
    public List<com.stolink.backend.domain.character.dto.RelationshipResponse> getRelationshipsByProjectId(
            UUID userId, UUID projectId) {
        List<Character> characters = getCharactersWithRelationships(userId, projectId);
        List<com.stolink.backend.domain.character.dto.RelationshipResponse> responses = new java.util.ArrayList<>();

        for (Character c : characters) {
            if (c.getRelationships() != null) {
                for (var r : c.getRelationships()) {
                    responses.add(com.stolink.backend.domain.character.dto.RelationshipResponse.builder()
                            .id(r.getId() != null ? r.getId().toString() : java.util.UUID.randomUUID().toString())
                            .sourceId(c.getId())
                            .targetId(r.getTargetId())
                            .types(r.getTypes())
                            .strength(r.getStrength())
                            .description(r.getDescription())
                            .bidirectional(r.getBidirectional())
                            .build());
                }
            }
        }
        return responses;
    }

    // @Transactional 제거: Neo4j driver.session()만 사용하므로 PostgreSQL 커넥션 불필요
    // 이전 코드에서 @Transactional이 있으면 메서드 시작 시 HikariCP에서 커넥션을 확보하고
    // Neo4j 응답을 기다리는 동안 PostgreSQL 커넥션이 유휴 상태로 점유되어 풀 고갈 발생
    public List<Character> getCharactersWithRelationships(UUID userId, UUID projectId) {
        log.info("Fetching characters manually (Robust Mode - Split Query) for project: {}", projectId);

        List<Character> characters = new ArrayList<>();
        Map<String, Character> characterMap = new java.util.HashMap<>();
        String pid = projectId.toString();

        try (var session = driver.session()) {

            // 1. Fetch ALL Characters first (Nodes only)
            // Reverted to fetching full node 'c' to ensure all JSON fields (profile, mbti,
            // etc.) are available for the frontend.
            // valid optimization: Split Query is maintained to avoid Cartesian product.
            var nodeResult = session.run("""
                    MATCH (c:Character)
                    WHERE c.project_id = $pid OR c.projectId = $pid
                    RETURN c
                    """, java.util.Map.of("pid", pid));

            while (nodeResult.hasNext()) {
                var record = nodeResult.next();
                var cNode = record.get("c").asNode();
                var cMap = cNode.asMap();

                Character character = new Character();

                // ID Handling
                if (cMap.containsKey("id")) {
                    character.setId((String) cMap.get("id"));
                } else if (cMap.containsKey("characterId")) {
                    character.setId((String) cMap.get("characterId"));
                } else {
                    character.setId(cNode.elementId());
                }

                if (cMap.containsKey("characterId")) {
                    character.setCharacterId((String) cMap.get("characterId"));
                }

                // Basic Fields
                character.setName((String) cMap.getOrDefault("name", "Unknown"));
                character.setRole((String) cMap.getOrDefault("role", "Unknown"));
                character.setImageUrl((String) cMap.getOrDefault("imageUrl", null));
                character.setProjectId(pid);

                character.setStatus((String) cMap.getOrDefault("status", null));
                character.setAge(cMap.containsKey("age") ? ((Number) cMap.get("age")).intValue() : null);
                character.setGender((String) cMap.getOrDefault("gender", null));
                character.setRace((String) cMap.getOrDefault("race", null));
                character.setMbti((String) cMap.getOrDefault("mbti", null));
                character.setBackstory((String) cMap.getOrDefault("backstory", null));
                character.setFaction((String) cMap.getOrDefault("faction", null));

                if (cMap.containsKey("positionX"))
                    character.setPositionX(((Number) cMap.get("positionX")).doubleValue());
                if (cMap.containsKey("positionY"))
                    character.setPositionY(((Number) cMap.get("positionY")).doubleValue());

                // JSON Fields
                character.setAliasesJson((String) cMap.getOrDefault("aliasesJson", null));
                character.setProfileJson((String) cMap.getOrDefault("profileJson", null));
                character.setAppearanceJson((String) cMap.getOrDefault("appearanceJson", null));
                character.setPersonalityJson((String) cMap.getOrDefault("personalityJson", null));
                character.setRelationsJson((String) cMap.getOrDefault("relationsJson", null));
                character.setCurrentMoodJson((String) cMap.getOrDefault("currentMoodJson", null));
                character.setMetaJson((String) cMap.getOrDefault("metaJson", null));
                character.setEmbeddingJson((String) cMap.getOrDefault("embeddingJson", null));
                character.setInventoryJson((String) cMap.getOrDefault("inventoryJson", null));
                character.setVisualJson((String) cMap.getOrDefault("visualJson", null));
                character.setMotivation((String) cMap.getOrDefault("motivation", null));
                character.setFirstAppearance((String) cMap.getOrDefault("firstAppearance", null));
                character.setExtrasJson((String) cMap.getOrDefault("extrasJson", null));

                character.setRelationships(new ArrayList<>()); // Initialize list

                characters.add(character);
                characterMap.put(character.getId(), character);
            }

            // 2. Fetch ALL Relationships (Edges) and map them in memory
            // This avoids the Cartesian product of "1 Character * N Relationships" in the
            // DB result
            var relResult = session.run("""
                    MATCH (c:Character)-[r]->(target:Character)
                    WHERE (c.project_id = $pid OR c.projectId = $pid)
                    RETURN c.id as sourceId, r, target
                    """, java.util.Map.of("pid", pid));

            while (relResult.hasNext()) {
                var record = relResult.next();
                String sourceId = record.get("sourceId").asString();
                var rel = record.get("r").asRelationship();
                var targetNode = record.get("target").asNode();

                Character sourceChar = characterMap.get(sourceId);
                if (sourceChar == null)
                    continue; // Should not happen if consistency is good

                // Target Mapping (Lightweight)
                var targetMap = targetNode.asMap();
                Character targetChar = new Character();
                if (targetMap.containsKey("characterId")) {
                    targetChar.setId((String) targetMap.get("characterId"));
                } else if (targetMap.containsKey("id")) {
                    targetChar.setId((String) targetMap.get("id"));
                } else {
                    targetChar.setId(targetNode.elementId());
                }
                targetChar.setName((String) targetMap.getOrDefault("name", "Unknown"));
                targetChar.setImageUrl((String) targetMap.getOrDefault("imageUrl", null));

                // Types
                List<String> typesList = new ArrayList<>();
                if (!rel.get("types").isNull()) {
                    typesList.addAll(rel.get("types").asList(org.neo4j.driver.Value::asString));
                } else {
                    typesList.add(rel.type());
                }

                CharacterRelationship charRel = CharacterRelationship.builder()
                        .source(sourceId)
                        .target(targetChar)
                        .types(typesList)
                        .strength(rel.get("strength").isNull() ? 0 : rel.get("strength").asInt())
                        .description(rel.get("description").asString(""))
                        .bidirectional(rel.get("bidirectional").asBoolean(false))
                        .projectId(pid)
                        .build();

                sourceChar.getRelationships().add(charRel);
            }

        } catch (Exception e) {
            log.error("Failed to fetch characters manually: {}", e.getMessage(), e);
            Character errorChar = new Character();
            errorChar.setId("error-1");
            errorChar.setName("ERROR: " + e.getMessage());
            errorChar.setRole("protagonist");
            characters.add(errorChar);
            return characters;
        }

        return characters;
    }

    @Transactional(readOnly = true)
    public Character getCharacterById(UUID userId, String characterId) {
        // Verify user existence
        getUserOrThrow(userId);

        Character character = characterRepository.findByIdWithRelationships(characterId)
                .orElseThrow(() -> new ResourceNotFoundException("Character", "id", characterId));

        // Populate source ID for relationships
        if (character.getRelationships() != null) {
            for (var rel : character.getRelationships()) {
                rel.setSource(character.getId());
            }
        }

        return character;
    }

    @Transactional
    public Character createCharacter(UUID userId, UUID projectId, Character character) {
        User user = getUserOrThrow(userId);
        Project project = getProjectOrThrow(projectId, user);

        character.setProjectId(project.getId().toString());
        character = characterRepository.save(character);

        log.info("Character created: {} in project: {}", character.getId(), projectId);
        return character;
    }

    @Transactional
    public void createRelationship(UUID userId, String sourceId, String targetId,
            List<String> types, Integer strength, String description) {
        createRelationship(userId, sourceId, targetId, types, strength, description, false);
    }

    @Transactional
    public void createRelationship(UUID userId, String sourceId, String targetId,
            List<String> types, Integer strength, String description, Boolean bidirectional) {
        // For simplicity, just create the relationship
        // In production, verify ownership of both characters
        // We need to find the projectId from one of the characters if not provided,
        // but here we can't easily without fetching them.
        // For now, if this is called from the controller without projectId, we might
        // have an issue.
        // However, createRelationship is usually called with knowledge of the project.
        // Let's assume we can fetch it or it's passed.
        Character source = characterRepository.findById(sourceId).orElse(null);
        String pId = (source != null) ? source.getProjectId() : null;

        characterRepository.createRelationship(sourceId, targetId, pId, types, strength, description, bidirectional);
        log.info("Relationship created: {} -> {}", sourceId, targetId);
    }

    @Transactional
    public void deleteCharacter(UUID userId, String characterId) {
        // In production, verify ownership
        characterRepository.deleteById(characterId);
        log.info("Character deleted: {}", characterId);
    }

    // =========== Relationship CRUD Operations ===========

    /**
     * 관계 생성 (프론트엔드 API용)
     * - 중복 확인 후 관계 생성
     * - bidirectional: true면 역방향 관계도 생성
     *
     * @return 생성된 관계 정보
     */
    @Transactional
    public com.stolink.backend.domain.character.dto.RelationshipResponse createRelationshipWithResponse(
            UUID userId, UUID projectId,
            com.stolink.backend.domain.character.dto.RelationshipCreateRequest request) {

        // 프로젝트 소유권 검증
        User user = getUserOrThrow(userId);
        Project project = getProjectOrThrow(projectId, user);
        String pId = project.getId().toString();

        String sourceId = request.sourceId();
        String targetId = request.targetId();

        // 중복 관계 확인
        Boolean exists = characterRepository.existsRelationship(sourceId, targetId);
        if (Boolean.TRUE.equals(exists)) {
            throw new com.stolink.backend.global.common.exception.DuplicateRelationshipException(sourceId, targetId);
        }

        // 소스/타겟 캐릭터 존재 확인
        if (!characterRepository.existsById(sourceId)) {
            throw new ResourceNotFoundException("Character", "id", sourceId);
        }
        if (!characterRepository.existsById(targetId)) {
            throw new ResourceNotFoundException("Character", "id", targetId);
        }

        // 관계 생성
        Long relId = characterRepository.createRelationshipReturningId(
                sourceId, targetId, pId,
                request.types(), request.strength(),
                request.description(), request.bidirectional());

        log.info("Relationship created: {} -> {} (id={})", sourceId, targetId, relId);

        // 양방향 관계 처리
        if (Boolean.TRUE.equals(request.bidirectional())) {
            // 역방향도 중복 확인
            Boolean reverseExists = characterRepository.existsRelationship(targetId, sourceId);
            if (!Boolean.TRUE.equals(reverseExists)) {
                Long reverseRelId = characterRepository.createRelationshipReturningId(
                        targetId, sourceId, pId,
                        request.types(), request.strength(),
                        request.description(), true);
                log.info("Reverse relationship created: {} -> {} (id={})", targetId, sourceId, reverseRelId);
            }
        }

        return com.stolink.backend.domain.character.dto.RelationshipResponse.builder()
                .id(String.valueOf(relId))
                .sourceId(sourceId)
                .targetId(targetId)
                .types(request.types())
                .strength(request.strength())
                .description(request.description())
                .bidirectional(request.bidirectional())
                .build();
    }

    // =========== Relationship CRUD Operations ===========

    /**
     * URL에 projectId가 없는 경우의 단일 관계 조회
     */
    @Transactional(readOnly = true)
    public com.stolink.backend.domain.character.dto.RelationshipResponse getRelationshipById(
            UUID userId, String relationshipId) {
        return handleRelationshipOperation(userId, null, relationshipId, "GET", null);
    }

    /**
     * URL에 projectId가 없는 경우의 관계 수정
     */
    @Transactional
    public com.stolink.backend.domain.character.dto.RelationshipResponse updateRelationship(
            UUID userId, String relationshipId,
            com.stolink.backend.domain.character.dto.RelationshipUpdateRequest request) {
        return handleRelationshipOperation(userId, null, relationshipId, "PATCH", request);
    }

    /**
     * URL에 projectId가 없는 경우의 관계 삭제
     */
    @Transactional
    public void deleteRelationship(UUID userId, String relationshipId) {
        handleRelationshipOperation(userId, null, relationshipId, "DELETE", null);
    }

    /**
     * 기존 컨트롤러 호환용 관계 수정 (projectId 포함)
     */
    @Transactional
    public com.stolink.backend.domain.character.dto.RelationshipResponse updateRelationship(
            UUID userId, UUID projectId, String relationshipId,
            com.stolink.backend.domain.character.dto.RelationshipUpdateRequest request) {
        return handleRelationshipOperation(userId, projectId, relationshipId, "PATCH", request);
    }

    /**
     * 기존 컨트롤러 호환용 관계 삭제 (projectId 포함)
     */
    @Transactional
    public void deleteRelationship(UUID userId, UUID projectId, String relationshipId) {
        handleRelationshipOperation(userId, projectId, relationshipId, "DELETE", null);
    }

    /**
     * 기존 컨트롤러 호환용 단일 관계 조회 (projectId 포함)
     */
    @Transactional(readOnly = true)
    public com.stolink.backend.domain.character.dto.RelationshipResponse getRelationshipById(
            UUID userId, UUID projectId, String relationshipId) {
        return handleRelationshipOperation(userId, projectId, relationshipId, "GET", null);
    }

    /**
     * 관계 작업을 통합 처리하는 내부 메서드
     * - ID 형식(Numeric vs Composite) 자동 감지
     * - projectId 검증 및 소유권 확인
     */
    private com.stolink.backend.domain.character.dto.RelationshipResponse handleRelationshipOperation(
            UUID userId, UUID projectId, String relationshipId, String operation,
            com.stolink.backend.domain.character.dto.RelationshipUpdateRequest request) {

        User user = getUserOrThrow(userId);
        com.stolink.backend.domain.character.repository.RelationshipProjection details = null;
        Long numericId = tryParseLong(relationshipId);

        if (numericId != null) {
            details = characterRepository.getRelationshipDetailsById(numericId).orElse(null);
        } else {
            String[] parts = parseCompositeId(relationshipId);
            details = characterRepository.getRelationshipDetailsBySourceTarget(parts[0], parts[1],
                    projectId != null ? projectId.toString() : null).orElse(null);
        }

        // PATCH (Update) operations with Composite ID can turn into Create (Upsert) if
        // missing
        boolean isUpsert = "PATCH".equals(operation) && numericId == null && details == null;

        if (!isUpsert && details == null) {
            throw new ResourceNotFoundException("Relationship", "id", relationshipId);
        }

        String pIdInDb = null;
        if (details != null) {
            // Project 소유권 검증 (If exists)
            pIdInDb = details.projectId();
            if (projectId != null && !projectId.toString().equals(pIdInDb)) {
                throw new IllegalArgumentException("Relationship does not belong to the specified project");
            }
        }

        // Resolve project and check ownership
        if (pIdInDb != null) {
            getProjectOrThrow(UUID.fromString(pIdInDb), user);
        } else if (projectId != null) {
            getProjectOrThrow(projectId, user);
            pIdInDb = projectId.toString();
        }

        switch (operation) {
            case "GET":
                return buildRelationshipResponse(details, String.valueOf(details.relId()));

            case "PATCH":
                com.stolink.backend.domain.character.repository.RelationshipProjection updated = null;
                boolean isCreated = false;

                if (numericId != null) {
                    updated = characterRepository.updateRelationship(numericId, request.types(),
                            request.strength(), request.description(), request.bidirectional(), request.since());
                } else {
                    String[] parts = parseCompositeId(relationshipId);
                    String sourceId = parts[0];
                    String targetId = parts[1];

                    if (details == null) {
                        // UPSERT: Relationship missing, create it
                        if (pIdInDb == null) {
                            // Fetch source char to get proper projectId
                            com.stolink.backend.domain.character.node.Character sourceChar = characterRepository
                                    .findById(sourceId)
                                    .orElseThrow(() -> new ResourceNotFoundException("Character", "id", sourceId));
                            pIdInDb = sourceChar.getProjectId();

                            // Check ownership
                            getProjectOrThrow(UUID.fromString(pIdInDb), user);
                        }

                        characterRepository.createRelationship(sourceId, targetId, pIdInDb,
                                request.types(), request.strength(), request.description(), request.bidirectional());

                        updated = characterRepository.getRelationshipDetailsBySourceTarget(sourceId, targetId, pIdInDb)
                                .orElseThrow(() -> new ResourceNotFoundException("Relationship", "id", relationshipId));
                        isCreated = true;
                        log.info("Relationship lazily created during update: {} -> {}", sourceId, targetId);
                    } else {
                        updated = characterRepository.updateRelationshipBySourceTarget(sourceId, targetId, pIdInDb,
                                request.types(), request.strength(), request.description(), request.bidirectional(),
                                request.since());
                    }
                }

                if (updated == null) {
                    throw new ResourceNotFoundException("Relationship", "id", relationshipId);
                }

                log.info("Relationship updated (created={}): {} ({})", isCreated, relationshipId, operation);
                return buildRelationshipResponse(updated, String.valueOf(updated.relId()));

            case "DELETE":
                Boolean bidirectional = details.bidirectional();
                String sourceId = details.sourceId();
                String targetId = details.targetId();

                if (Boolean.TRUE.equals(bidirectional)) {
                    characterRepository.deleteRelationshipBySourceTarget(targetId, sourceId, pIdInDb);
                    log.info("Reverse relationship deleted: {} -> {}", targetId, sourceId);
                }

                if (numericId != null) {
                    characterRepository.deleteRelationshipById(numericId);
                } else {
                    characterRepository.deleteRelationshipBySourceTarget(sourceId, targetId, pIdInDb);
                }
                log.info("Relationship deleted: {}", relationshipId);
                return null;

            default:
                throw new UnsupportedOperationException("Unsupported relationship operation: " + operation);
        }
    }

    private Long tryParseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 복합 ID 파싱 (sourceId-targetId 형식)
     * UUID-UUID 형식을 파싱
     */
    private String[] parseCompositeId(String compositeId) {
        // UUID는 36자 (8-4-4-4-12 형식)
        // 복합 ID는 "uuid1-uuid2" 형식으로 총 73자
        if (compositeId.length() >= 73) {
            String sourceId = compositeId.substring(0, 36);
            String targetId = compositeId.substring(37); // Skip the hyphen
            return new String[] { sourceId, targetId };
        }
        // 파싱 실패 시 그냥 하이픈으로 분리 시도
        String[] parts = compositeId.split("-");
        if (parts.length >= 10) {
            // 두 UUID가 합쳐진 경우: 5개씩 분리
            String sourceId = String.join("-", java.util.Arrays.copyOfRange(parts, 0, 5));
            String targetId = String.join("-", java.util.Arrays.copyOfRange(parts, 5, 10));
            return new String[] { sourceId, targetId };
        }
        // If it's just a regular split
        parts = compositeId.split("-", 2);
        if (parts.length == 2) {
            return parts;
        }
        throw new IllegalArgumentException("Invalid relationship ID format: " + compositeId);
    }

    private com.stolink.backend.domain.character.dto.RelationshipResponse buildRelationshipResponse(
            com.stolink.backend.domain.character.repository.RelationshipProjection data, String relationshipId) {
        return com.stolink.backend.domain.character.dto.RelationshipResponse.builder()
                .id(relationshipId)
                .sourceId(data.sourceId())
                .targetId(data.targetId())
                .types(data.types())
                .strength(data.strength())
                .description(data.description())
                .bidirectional(data.bidirectional())
                .since(data.since())
                .build();
    }

    @org.springframework.transaction.annotation.Transactional
    public void seedDummyData() {
        try {
            log.info("Starting PostgreSQL data seeding for target user...");

            // 1. Fetch User (using the ID provided by the user)
            UUID targetUserId = UUID.fromString("00c4b012-d8e1-4265-8a2e-08be3eba0198");
            com.stolink.backend.domain.user.entity.User user = userRepository.findById(targetUserId)
                    .orElseGet(() -> userRepository.save(com.stolink.backend.domain.user.entity.User.builder()
                            .id(targetUserId)
                            .email("dongha@example.com")
                            .password("password")
                            .nickname("Dongha")
                            .build()));
            log.info("User ready: {}", user.getId());

            // 2. Cleanup existing dummy project by title if it exists
            String projectTitle = "Les Misérables";
            // Updated to match the user's testing project ID
            UUID targetProjectId = UUID.fromString("b958f822-f231-4f9f-a8a9-2d728ed66ae0");

            projectRepository.findById(targetProjectId).ifPresent(p -> {
                log.info("Cleaning up existing '{}' project...", projectTitle);
                documentRepository.deleteAllByProject(p);
                projectRepository.delete(p);
                projectRepository.flush();
                entityManager.clear();
            });

            // 3. Create fresh Project with FIXED ID
            com.stolink.backend.domain.project.entity.Project project = projectRepository
                    .save(com.stolink.backend.domain.project.entity.Project.builder()
                            .id(targetProjectId)
                            .user(user)
                            .title(projectTitle)
                            .description("A historical novel by Victor Hugo.")
                            .genre(com.stolink.backend.domain.project.entity.Project.Genre.DRAMA)
                            .status(com.stolink.backend.domain.project.entity.Project.ProjectStatus.WRITING)
                            .author("Victor Hugo")
                            .coverImage(
                                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c3/Les_Miserables_1862_Clive_Farrar.jpg/440px-Les_Miserables_1862_Clive_Farrar.jpg")
                            .build());

            log.info("Seeded Project ID: {}", project.getId());

            // 4. Seed Documents for this project
            documentRepository.saveAll(java.util.List.of(
                    com.stolink.backend.domain.document.entity.Document.builder()
                            .project(project)
                            .title("Chapter 1: Jean Valjean")
                            .content("In 1815, M. Charles-François-Bienvenu Myriel was Bishop of Digne...")
                            .type(com.stolink.backend.domain.document.entity.Document.DocumentType.TEXT)
                            .status(com.stolink.backend.domain.document.entity.Document.DocumentStatus.DRAFT)
                            .order(1)
                            .wordCount(100)
                            .includeInCompile(true)
                            .build(),
                    com.stolink.backend.domain.document.entity.Document.builder()
                            .project(project)
                            .title("Chapter 2: The Fall")
                            .content("The evening before, Jean Valjean had entered Digne.")
                            .type(com.stolink.backend.domain.document.entity.Document.DocumentType.TEXT)
                            .status(com.stolink.backend.domain.document.entity.Document.DocumentStatus.DRAFT)
                            .order(2)
                            .wordCount(150)
                            .includeInCompile(true)
                            .build()));
            log.info("Seeded Documents successfully.");

            // 5. Seed Neo4j Data (20 Characters)
            try (var session = driver.session()) {
                String pid = targetProjectId.toString();
                log.info("Seeding 20 characters for project: {}...", pid);

                // @SuppressWarnings("deprecation") // Suppress if writeTransaction is
                // deprecated but available
                session.executeWrite(tx -> {
                    tx.run("MATCH (n:Character {projectId: $pid}) DETACH DELETE n", java.util.Map.of("pid", pid));
                    return null;
                });

                session.executeWrite(tx -> {
                    tx.run("""
                            // 20 Characters
                            CREATE (v:Character {id: randomUUID(), projectId: $pid, name: 'Jean Valjean', role: 'protagonist', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Valjean'})
                            CREATE (j:Character {id: randomUUID(), projectId: $pid, name: 'Javert', role: 'antagonist', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Javert'})
                            CREATE (f:Character {id: randomUUID(), projectId: $pid, name: 'Fantine', role: 'supporting', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Fantine'})
                            CREATE (c:Character {id: randomUUID(), projectId: $pid, name: 'Cosette', role: 'supporting', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Cosette'})
                            CREATE (m:Character {id: randomUUID(), projectId: $pid, name: 'Marius', role: 'sidekick', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Marius'})
                            CREATE (e:Character {id: randomUUID(), projectId: $pid, name: 'Eponine', role: 'supporting', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Eponine'})
                            CREATE (t:Character {id: randomUUID(), projectId: $pid, name: 'Thenardier', role: 'antagonist', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Thenardier'})
                            CREATE (mt:Character {id: randomUUID(), projectId: $pid, name: 'Mme Thenardier', role: 'antagonist', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=MmeThenardier'})
                            CREATE (g:Character {id: randomUUID(), projectId: $pid, name: 'Gavroche', role: 'sidekick', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Gavroche'})
                            CREATE (en:Character {id: randomUUID(), projectId: $pid, name: 'Enjolras', role: 'supporting', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Enjolras'})
                            CREATE (gr:Character {id: randomUUID(), projectId: $pid, name: 'Grantaire', role: 'supporting', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Grantaire'})
                            CREATE (bm:Character {id: randomUUID(), projectId: $pid, name: 'Bishop Myriel', role: 'mentor', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Bishop'})
                            CREATE (co:Character {id: randomUUID(), projectId: $pid, name: 'Combeferre', role: 'supporting', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Combeferre'})
                            CREATE (cu:Character {id: randomUUID(), projectId: $pid, name: 'Courfeyrac', role: 'sidekick', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Courfeyrac'})
                            CREATE (jp:Character {id: randomUUID(), projectId: $pid, name: 'Jean Prouvaire', role: 'other', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Prouvaire'})
                            CREATE (fe:Character {id: randomUUID(), projectId: $pid, name: 'Feuilly', role: 'supporting', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Feuilly'})
                            CREATE (ba:Character {id: randomUUID(), projectId: $pid, name: 'Bahorel', role: 'other', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Bahorel'})
                            CREATE (jo:Character {id: randomUUID(), projectId: $pid, name: 'Joly', role: 'other', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Joly'})
                            CREATE (bo:Character {id: randomUUID(), projectId: $pid, name: 'Bossuet', role: 'other', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Bossuet'})
                            CREATE (az:Character {id: randomUUID(), projectId: $pid, name: 'Azelma', role: 'other', imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Azelma'})

                            // Relationships with types as array
                            CREATE (v)-[:RELATED_TO {projectId: $pid, types: ['ENEMY'], strength: 9, description: 'Obsessive Pursuer'}]->(j)
                            CREATE (j)-[:RELATED_TO {projectId: $pid, types: ['ENEMY'], strength: 9, description: 'Target'}]->(v)
                            CREATE (m)-[:RELATED_TO {projectId: $pid, types: ['ROMANTIC'], strength: 10, description: 'True Love'}]->(c)
                            CREATE (c)-[:RELATED_TO {projectId: $pid, types: ['ROMANTIC'], strength: 10, description: 'True Love'}]->(m)
                            CREATE (v)-[:RELATED_TO {projectId: $pid, types: ['ALLY'], strength: 10, description: 'Guardian'}]->(c)
                            CREATE (f)-[:RELATED_TO {projectId: $pid, types: ['FAMILY'], strength: 10, description: 'Biological Mother'}]->(c)
                            CREATE (e)-[:RELATED_TO {projectId: $pid, types: ['ROMANTIC'], strength: 7, description: 'Unrequited Love'}]->(m)
                            CREATE (t)-[:RELATED_TO {projectId: $pid, types: ['ENEMY'], strength: 8, description: 'Blackmailer'}]->(v)
                            CREATE (en)-[:RELATED_TO {projectId: $pid, types: ['ALLY'], strength: 9, description: 'Leader and Follower'}]->(gr)
                            CREATE (g)-[:RELATED_TO {projectId: $pid, types: ['ALLY'], strength: 8, description: 'Street Ally'}]->(en)
                            CREATE (bm)-[:RELATED_TO {projectId: $pid, types: ['ALLY'], strength: 10, description: 'Spiritual Savior'}]->(v)
                            CREATE (co)-[:RELATED_TO {projectId: $pid, types: ['ALLY'], strength: 8, description: 'ABC Friends'}]->(en)
                            CREATE (cu)-[:RELATED_TO {projectId: $pid, types: ['ALLY'], strength: 8, description: 'ABC Friends'}]->(m)
                            CREATE (t)-[:RELATED_TO {projectId: $pid, types: ['FAMILY'], strength: 5, description: 'Spouse/Partner'}]->(mt)
                            CREATE (t)-[:RELATED_TO {projectId: $pid, types: ['FAMILY'], strength: 7, description: 'Father'}]->(e)
                            CREATE (mt)-[:RELATED_TO {projectId: $pid, types: ['FAMILY'], strength: 7, description: 'Mother'}]->(az)
                            """,
                            java.util.Map.of("pid", pid));
                    return null;
                });
                log.info("Comprehensive Les Misérables data seeded successfully.");
            }
        } catch (Exception e) {
            log.error("Seeding Failed: {}", e.getMessage(), e);
            throw new RuntimeException("Seeding Failed", e);
        }
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private Project getProjectOrThrow(UUID projectId, User user) {
        return projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }

    // 캐릭터 이미지 생성 요청 (RabbitMQ로 전송)
    // - userId로 Project 소유권 검증 (경합 조건 방지)
    // - ImageGenerationTask를 DB에 저장하여 추적 가능하게 함
    // - RabbitMQ 메시지 전송은 @TransactionalEventListener로 트랜잭션 커밋 후 수행
    @Transactional
    public String triggerImageGeneration(
            UUID userId,
            UUID projectId,
            UUID characterId,
            String description,
            String action,
            Map<String, Object> setting) {
        // userId로 Project 소유권 검증 (경합 조건 방지)
        User user = getUserOrThrow(userId);
        Project project = getProjectOrThrow(projectId, user);

        // Fetch character to get current image URL (needed for edit)
        Character character = characterRepository.findById(characterId.toString())
                .filter(c -> project.getId().toString().equals(c.getProjectId()))
                .orElseThrow(() -> new ResourceNotFoundException("Character", "id", characterId));

        String jobId = UUID.randomUUID().toString();
        String safeAction = (action == null || action.isBlank()) ? "create" : action;
        String originalImageUrl = "edit".equalsIgnoreCase(safeAction) ? character.getImageUrl() : null;

        log.info("Before URL Replace - action: {}, url: {}", safeAction, originalImageUrl);
        // Docker 환경 호환성: localhost URL을 내부 컨테이너 호스트명으로 변환
        if (originalImageUrl != null && originalImageUrl.contains("localhost")) {
            originalImageUrl = originalImageUrl.replace("localhost", "stolink-minio-standalone");
            log.info("After URL Replace: {}", originalImageUrl);
        }

        // Generate comprehensive prompt
        Map<String, Object> appearance = Collections.emptyMap();
        if (character.getAppearanceJson() != null) {
            try {
                appearance = objectMapper.readValue(character.getAppearanceJson(),
                        new TypeReference<Map<String, Object>>() {
                        });
            } catch (Exception e) {
                log.warn("Failed to parse appearanceJson for character {}", character.getId());
            }
        }
        String fullPrompt = generatePrompt(character, appearance, setting, description);

        // ImageGenerationTask를 DB에 저장 (콜백 처리 및 재시도를 위함)
        com.stolink.backend.domain.character.entity.ImageGenerationTask task = com.stolink.backend.domain.character.entity.ImageGenerationTask
                .builder()
                .jobId(jobId)
                .userId(userId)
                .projectId(project.getId())
                .characterId(characterId)
                .description(fullPrompt) // Save the FULL generated prompt
                .status(com.stolink.backend.domain.character.entity.ImageGenerationTask.TaskStatus.PENDING)
                .build();

        // Extract and Store Setting Prompts if available
        if (setting != null) {
            String settingIdStr = (String) setting.get("settingId");
            if (settingIdStr != null) {
                try {
                    task.setSettingId(UUID.fromString(settingIdStr));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid settingId format: {}", settingIdStr);
                }
            }
            task.setVisualBackground((String) setting.get("visual_background"));
            task.setAtmosphere((String) setting.get("atmosphere"));
            task.setLighting((String) setting.get("lighting"));
            task.setTimeOfDay((String) setting.get("time_of_day"));
            task.setArtStyle((String) setting.get("art_style"));
        }

        imageGenerationTaskRepository.save(task);

        // 트랜잭션 커밋 후 메시지 발송을 위한 이벤트 발행
        eventPublisher.publishEvent(new ImageGenerationRequestedEvent(
                jobId, userId, project.getId(), characterId, fullPrompt, safeAction, originalImageUrl));

        log.info(
                "Image generation task created and event published: jobId={}, userId={}, projectId={}, characterId={}, action={}",
                jobId, userId, projectId, characterId, safeAction);

        return jobId;
    }

    // 트랜잭션 커밋 후 RabbitMQ 이미지 생성 태스크 전송
    // @TransactionalEventListener(phase = AFTER_COMMIT): 트랜잭션이 성공적으로 커밋된 후에만 실행됨
    // 실패 시 ImageGenerationTask 상태를 FAILED로 업데이트하여 재시도 가능하게 함
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onImageGenerationRequested(ImageGenerationRequestedEvent event) {
        log.info("onImageGenerationRequested triggered for jobId: {}", event.jobId());
        try {
            // 헬스체크: 이미지 서버가 정상이 아니면 예외 발생
            imageServerHealthChecker.checkHealthOrThrow();

            ImageGenerationTaskDTO task = ImageGenerationTaskDTO.builder()
                    .jobId(event.jobId())
                    .userId(event.userId())
                    .projectId(event.projectId())
                    .characterId(event.characterId())
                    .message(event.description())
                    .action(event.action())
                    .originalImageUrl(event.originalImageUrl())
                    .callbackUrl(buildCallbackUrl())
                    .build();

            producerService.sendImageGenerationTask(task);

            // 전송 성공 시 상태 업데이트
            imageGenerationTaskRepository.findById(event.jobId()).ifPresent(t -> {
                t.markAsSent();
                imageGenerationTaskRepository.save(t);
            });

            log.info("Image generation task sent to RabbitMQ: jobId={}", event.jobId());
        } catch (Throwable e) {
            // 트랜잭션은 이미 커밋됨 - throw해도 롤백 불가
            // ImageGenerationTask 상태를 FAILED로 업데이트하여 재시도 가능하게 함
            log.error("Failed to send image generation task: jobId={}, error={}",
                    event.jobId(), e.getMessage(), e);

            imageGenerationTaskRepository.findById(event.jobId()).ifPresent(t -> {
                t.markAsFailed("RabbitMQ send failed: " + e.getMessage());
                t.incrementRetryCount();
                imageGenerationTaskRepository.save(t);
            });
        }
    }

    // AI 콜백 URL 생성
    private String buildCallbackUrl() {
        return callbackBaseUrl + "/api/internal/ai/image/callback";
    }

    /**
     * 캐릭터 이미지 URL 업데이트 (AI 이미지 생성 완료 후 콜백에서 호출)
     *
     * @param characterId 캐릭터 ID
     * @param imageUrl    생성된 이미지 URL
     */
    @Transactional
    public void updateCharacterImageUrl(UUID characterId, String imageUrl) {
        Character updatedCharacter = characterRepository.updateImageUrl(
                characterId.toString(), imageUrl);

        if (updatedCharacter == null) {
            log.warn("Character not found for imageUrl update: characterId={}", characterId);
            throw new ResourceNotFoundException("Character", "id", characterId);
        }

        log.info("Character imageUrl updated: characterId={}, imageUrl={}", characterId, imageUrl);
    }

    /**
     * 캐릭터 정보 업데이트 (이름, 역할, 위치, 외형 등)
     *
     * @param userId      사용자 ID
     * @param characterId 캐릭터 ID
     * @param positionX   X 좌표
     * @param positionY   Y 좌표
     * @return 업데이트된 캐릭터
     */
    @Transactional
    public Character updateCharacter(UUID userId, String characterId,
            com.stolink.backend.domain.character.dto.CharacterUpdateRequest request) {
        // Verify user existence
        getUserOrThrow(userId);

        String appearanceJson = toJson(request.getAppearance());
        String profileJson = toJson(request.getProfile());
        String personalityJson = toJson(request.getPersonality());
        String currentMoodJson = toJson(request.getCurrentMood());
        String inventoryJson = toJson(request.getInventory());

        Character updated = characterRepository.updateCharacterFull(
                characterId,
                request.getName(),
                request.getRole(),
                request.getImageUrl(),
                request.getPositionX(),
                request.getPositionY(),
                appearanceJson,
                profileJson,
                personalityJson,
                currentMoodJson,
                inventoryJson);

        if (updated == null) {
            throw new ResourceNotFoundException("Character", "id", characterId);
        }

        log.info("Character updated: id={}", characterId);
        return updated;
    }

    private String toJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.warn("Failed to serialize object to JSON: {}", e.getMessage());
            return null;
        }
    }

    private String generatePrompt(Character character, Map<String, Object> appearance, Map<String, Object> setting,
            String userInstructions) {
        StringBuilder prompt = new StringBuilder();

        // 1. Basic Stats
        prompt.append("Character: ");
        if (character.getAge() != null)
            prompt.append(character.getAge()).append("-year-old ");
        if (character.getGender() != null)
            prompt.append(character.getGender()).append(" ");
        if (character.getRace() != null)
            prompt.append(character.getRace()).append(" ");
        if (character.getName() != null)
            prompt.append(character.getName());
        if (character.getRole() != null)
            prompt.append(" (").append(character.getRole()).append(")");
        prompt.append(". ");

        // 2. Appearance
        if (appearance != null && !appearance.isEmpty()) {
            prompt.append("Appearance: ");
            appearance.forEach((k, v) -> {
                if (v != null && !v.toString().isBlank()) {
                    prompt.append(k).append(": ").append(v).append(", ");
                }
            });
            prompt.append(" ");
        }

        // 3. Setting / Background
        if (setting != null) {
            prompt.append("\nBackground: ");
            if (setting.get("location_name") != null)
                prompt.append(setting.get("location_name")).append(", ");
            if (setting.get("visual_background") != null)
                prompt.append(setting.get("visual_background")).append(", ");
            if (setting.get("atmosphere") != null)
                prompt.append("Atmosphere: ").append(setting.get("atmosphere")).append(", ");
            if (setting.get("lighting") != null)
                prompt.append("Lighting: ").append(setting.get("lighting")).append(", ");
            if (setting.get("time_of_day") != null)
                prompt.append("Time: ").append(setting.get("time_of_day")).append(", ");
        }

        // 4. User Instructions
        if (userInstructions != null && !userInstructions.isBlank()) {
            prompt.append("\nAdditional Request: ").append(userInstructions);
        }

        return prompt.toString().trim();
    }

    /**
     * 프로젝트 복제: Characters 및 Relationships 복제
     *
     * @param sourceProject 원본 프로젝트
     * @param targetProject 복제 대상 프로젝트
     */
    @Transactional
    public void cloneCharactersAndRelationships(Project sourceProject, Project targetProject) {
        String sourceProjectId = sourceProject.getId().toString();
        String targetProjectId = targetProject.getId().toString();

        // log.debug("Starting character clone form {} to {}", sourceProjectId,
        // targetProjectId);

        // 1. 캐릭터 복제
        List<Character> sourceCharacters = characterRepository.findByProjectId(sourceProjectId);
        // log.debug("Found {} characters to clone", sourceCharacters.size());

        java.util.Map<String, String> oldToNewCharIdMap = new java.util.HashMap<>();

        List<Character> newCharacters = new java.util.ArrayList<>();
        for (Character source : sourceCharacters) {
            Character newChar = Character.builder()
                    .projectId(targetProjectId)
                    .characterId(source.getCharacterId())
                    .name(source.getName())
                    .role(source.getRole())
                    .status(source.getStatus())
                    .age(source.getAge())
                    .gender(source.getGender())
                    .race(source.getRace())
                    .mbti(source.getMbti())
                    .backstory(source.getBackstory())
                    .faction(source.getFaction())
                    .imageUrl(source.getImageUrl())
                    .positionX(source.getPositionX())
                    .positionY(source.getPositionY())
                    .aliasesJson(source.getAliasesJson())
                    .profileJson(source.getProfileJson())
                    .appearanceJson(source.getAppearanceJson())
                    .personalityJson(source.getPersonalityJson())
                    .relationsJson(source.getRelationsJson())
                    .currentMoodJson(source.getCurrentMoodJson())
                    .metaJson(source.getMetaJson())
                    .embeddingJson(source.getEmbeddingJson())
                    .inventoryJson(source.getInventoryJson())
                    .visualJson(source.getVisualJson())
                    .motivation(source.getMotivation())
                    .firstAppearance(source.getFirstAppearance())
                    .extrasJson(source.getExtrasJson())
                    .build();
            newCharacters.add(newChar);
        }

        List<Character> savedCharacters = characterRepository.saveAll(newCharacters);

        // Map saved characters by business key (characterId) to ensure correct ID
        // mapping
        java.util.Map<String, Character> savedCharMap = savedCharacters.stream()
                .filter(c -> c.getCharacterId() != null)
                .collect(java.util.stream.Collectors.toMap(Character::getCharacterId,
                        java.util.function.Function.identity(), (a, b) -> a));

        for (Character source : sourceCharacters) {
            Character saved = savedCharMap.get(source.getCharacterId());
            if (saved != null) {
                oldToNewCharIdMap.put(source.getId(), saved.getId());
            } else {
                log.warn("Failed to map saved character for source ID: {}", source.getId());
            }
        }

        // 2. 관계 복제 (모든 관계 타입 지원 - APOC 없이)
        if (!oldToNewCharIdMap.isEmpty()) {
            try (var session = driver.session()) {
                // 지원하는 모든 관계 타입
                String[] relationshipTypes = { "RELATED_TO", "ALLY", "ENEMY", "RIVAL", "ROMANTIC", "FAMILY",
                        "NEUTRAL" };

                for (String relType : relationshipTypes) {
                    session.executeWrite(tx -> {
                        tx.run("""
                                UNWIND keys($idMap) AS sourceId
                                MATCH (source:Character {id: sourceId})-[r:%s]-(target:Character)
                                WHERE (source.projectId = $sourceProjectId OR source.project_id = $sourceProjectId)
                                  AND target.id IN keys($idMap)
                                  AND source.id < target.id
                                WITH r, startNode(r) AS relStart, endNode(r) AS relEnd, $idMap AS idMap
                                WITH r, idMap[relStart.id] AS newStartId, idMap[relEnd.id] AS newTargetId
                                MATCH (newStart:Character {id: newStartId})
                                MATCH (newEnd:Character {id: newTargetId})
                                CREATE (newStart)-[newR:%s]->(newEnd)
                                SET newR = properties(r),
                                    newR.projectId = $targetProjectId,
                                    newR.id = randomUUID()
                                """.formatted(relType, relType),
                                java.util.Map.of(
                                        "sourceProjectId", sourceProjectId,
                                        "targetProjectId", targetProjectId,
                                        "idMap", oldToNewCharIdMap));
                        return null;
                    });
                }
            }
        }

        log.info("Cloned {} characters and relationships from project {} to {}",
                oldToNewCharIdMap.size(), sourceProjectId, targetProjectId);
    }

    /**
     * 프로젝트 ID로 모든 캐릭터 및 관계 삭제 (보상 트랜잭션용)
     */
    @Transactional
    public void deleteCharactersByProjectId(UUID projectId) {
        String pid = projectId.toString();
        characterRepository.deleteAllByProjectId(pid);

        // 관계도 삭제가 필요한 경우 Cypher 실행 (deleteAllByProjectId가 노드 삭제 시 관계도 삭제하는지 확인 필요)
        // Spring Data Neo4j repositories usually detach delete.

        // Manual cleanup just in case
        try (var session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH (n:Character) WHERE n.projectId = $pid OR n.project_id = $pid DETACH DELETE n",
                        java.util.Map.of("pid", pid));
                return null;
            });
        }
        log.info("Deleted characters for project {} (Compensation)", pid);
    }
}
