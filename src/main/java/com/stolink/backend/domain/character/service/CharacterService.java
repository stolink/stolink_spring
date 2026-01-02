package com.stolink.backend.domain.character.service;

import com.stolink.backend.domain.ai.dto.ImageGenerationTaskDTO;
import com.stolink.backend.domain.ai.service.ImageServerHealthChecker;
import com.stolink.backend.domain.ai.service.RabbitMQProducerService;
import com.stolink.backend.domain.character.event.ImageGenerationRequestedEvent;
import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.repository.CharacterRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    public List<Character> getCharactersWithRelationships(UUID userId, UUID projectId) {
        User user = getUserOrThrow(userId);
        Project project = getProjectOrThrow(projectId, user);

        List<Character> characters = characterRepository
                .findAllWithRelationshipsByProjectId(project.getId().toString());

        // Populate source ID for each relationship to help frontend graph mapping
        for (Character character : characters) {
            if (character.getRelationships() != null) {
                for (var rel : character.getRelationships()) {
                    rel.setSource(character.getId());
                }
            }
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
            String type, Integer strength, String description) {
        // For simplicity, just create the relationship
        // In production, verify ownership of both characters
        characterRepository.createRelationship(sourceId, targetId, type, strength, description);
        log.info("Relationship created: {} -> {}", sourceId, targetId);
    }

    @Transactional
    public void deleteCharacter(UUID userId, String characterId) {
        // In production, verify ownership
        characterRepository.deleteById(characterId);
        log.info("Character deleted: {}", characterId);
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
            UUID targetProjectId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

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

                            // Relationships
                            CREATE (v)-[:RELATED_TO {type: 'enemy', strength: 9, description: 'Obsessive Pursuer'}]->(j)
                            CREATE (j)-[:RELATED_TO {type: 'enemy', strength: 9, description: 'Target'}]->(v)
                            CREATE (m)-[:RELATED_TO {type: 'lover', strength: 10, description: 'True Love'}]->(c)
                            CREATE (c)-[:RELATED_TO {type: 'lover', strength: 10, description: 'True Love'}]->(m)
                            CREATE (v)-[:RELATED_TO {type: 'friend', strength: 10, description: 'Guardian'}]->(c)
                            CREATE (f)-[:RELATED_TO {type: 'lover', strength: 10, description: 'Biological Mother'}]->(c)
                            CREATE (e)-[:RELATED_TO {type: 'lover', strength: 7, description: 'Unrequited Love'}]->(m)
                            CREATE (t)-[:RELATED_TO {type: 'enemy', strength: 8, description: 'Blackmailer'}]->(v)
                            CREATE (en)-[:RELATED_TO {type: 'friend', strength: 9, description: 'Leader and Follower'}]->(gr)
                            CREATE (g)-[:RELATED_TO {type: 'friend', strength: 8, description: 'Street Ally'}]->(en)
                            CREATE (bm)-[:RELATED_TO {type: 'friend', strength: 10, description: 'Spiritual Savior'}]->(v)
                            CREATE (co)-[:RELATED_TO {type: 'friend', strength: 8, description: 'ABC Friends'}]->(en)
                            CREATE (cu)-[:RELATED_TO {type: 'friend', strength: 8, description: 'ABC Friends'}]->(m)
                            CREATE (t)-[:RELATED_TO {type: 'friend', strength: 5, description: 'Spouse/Partner'}]->(mt)
                            CREATE (t)-[:RELATED_TO {type: 'friend', strength: 7, description: 'Father'}]->(e)
                            CREATE (mt)-[:RELATED_TO {type: 'friend', strength: 7, description: 'Mother'}]->(az)
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
                .filter(c -> c.getProjectId().equals(project.getId().toString()))
                .orElseThrow(() -> new ResourceNotFoundException("Character", "id", characterId));

        String jobId = UUID.randomUUID().toString();
        String safeAction = (action == null || action.isBlank()) ? "create" : action;
        String originalImageUrl = "edit".equalsIgnoreCase(safeAction) ? character.getImageUrl() : null;

        // Generate comprehensive prompt
        Map<String, Object> appearance = Collections.emptyMap();
        if (character.getAppearanceJson() != null) {
            try {
                appearance = objectMapper.readValue(character.getAppearanceJson(), Map.class);
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
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onImageGenerationRequested(ImageGenerationRequestedEvent event) {
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
        } catch (Exception e) {
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
        return callbackBaseUrl + "/image/callback";
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
}
