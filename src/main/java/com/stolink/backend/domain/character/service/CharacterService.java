package com.stolink.backend.domain.character.service;

import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.repository.CharacterRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final com.stolink.backend.domain.document.repository.DocumentRepository documentRepository;
    private final org.neo4j.driver.Driver driver;
    private final jakarta.persistence.EntityManager entityManager;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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

        return characterRepository.findAllWithRelationshipsByProjectId(project.getId().toString());
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
            log.info("Starting PostgreSQL data seeding...");

            // 1. Seed/Fetch User
            String email = "dummy@stolink.com";
            com.stolink.backend.domain.user.entity.User user = userRepository.findByEmail(email)
                    .orElseGet(() -> userRepository.save(com.stolink.backend.domain.user.entity.User.builder()
                            .email(email)
                            .password("password")
                            .nickname("Victor Hugo")
                            .avatarUrl("https://api.dicebear.com/7.x/pixel-art/svg?seed=Victor")
                            .build()));
            log.info("User ready: {}", user.getId());

            // 2. Cleanup existing dummy project by title if it exists
            String projectTitle = "Les Misérables";
            projectRepository.findByTitleAndUser(projectTitle, user).ifPresent(p -> {
                log.info("Cleaning up existing '{}' project...", projectTitle);
                documentRepository.deleteAllByProject(p);
                projectRepository.delete(p);
                projectRepository.flush();
                entityManager.clear();
            });

            // 3. Create fresh Project with generated ID
            com.stolink.backend.domain.project.entity.Project project = projectRepository
                    .save(com.stolink.backend.domain.project.entity.Project.builder()
                            .user(user)
                            .title(projectTitle)
                            .description("A historical novel by Victor Hugo.")
                            .genre(com.stolink.backend.domain.project.entity.Project.Genre.DRAMA)
                            .status(com.stolink.backend.domain.project.entity.Project.ProjectStatus.WRITING)
                            .author("Victor Hugo")
                            .coverImage(
                                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c3/Les_Miserables_1862_Clive_Farrar.jpg/440px-Les_Miserables_1862_Clive_Farrar.jpg")
                            .build());

            UUID realProjectId = project.getId();
            log.info("Seeded Project ID: {}", realProjectId);

            // 4. Seed Documents for this project
            documentRepository.saveAll(java.util.List.of(
                    com.stolink.backend.domain.document.entity.Document.builder()
                            .project(project)
                            .title("Chapter 1: Jean Valjean")
                            .content(
                                    "In 1815, M. Charles-François-Bienvenu Myriel was Bishop of Digne. He was an old man of about seventy-five years of age; he had occupied the see of Digne since 1806.")
                            .type(com.stolink.backend.domain.document.entity.Document.DocumentType.TEXT)
                            .status(com.stolink.backend.domain.document.entity.Document.DocumentStatus.DRAFT)
                            .order(1)
                            .wordCount(100)
                            .includeInCompile(true)
                            .build(),
                    com.stolink.backend.domain.document.entity.Document.builder()
                            .project(project)
                            .title("Chapter 2: The Fall")
                            .content("The evening before, Jean Valjean had entered Digne. He was an outcast.")
                            .type(com.stolink.backend.domain.document.entity.Document.DocumentType.TEXT)
                            .status(com.stolink.backend.domain.document.entity.Document.DocumentStatus.DRAFT)
                            .order(2)
                            .wordCount(150)
                            .includeInCompile(true)
                            .build(),
                    com.stolink.backend.domain.document.entity.Document.builder()
                            .project(project)
                            .title("Notes & Ideas")
                            .type(com.stolink.backend.domain.document.entity.Document.DocumentType.FOLDER)
                            .status(com.stolink.backend.domain.document.entity.Document.DocumentStatus.DRAFT)
                            .order(3)
                            .wordCount(0)
                            .includeInCompile(true)
                            .build()));
            log.info("Seeded Documents successfully.");

            // 5. Seed Neo4j Data (Characters) using the generated realProjectId
            try (var session = driver.session()) {
                log.info("Seeding dummy character data for project: {}...", realProjectId);

                // Clear existing dummy data for this project to ensure fresh seed
                session.writeTransaction(tx -> {
                    tx.run("MATCH (n:Character {projectId: $pid}) DETACH DELETE n",
                            java.util.Map.of("pid", realProjectId.toString()));
                    return null;
                });

                // Create characters and relationships
                session.writeTransaction(tx -> {
                    tx.run("""
                            CREATE (valjean:Character {
                                id: randomUUID(),
                                projectId: $pid,
                                name: 'Jean Valjean',
                                role: 'protagonist',
                                imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=JeanValjean',
                                `extras.age`: 55,
                                `extras.gender`: 'Male',
                                `extras.description`: 'A former convict seeking redemption.',
                                createdAt: datetime(),
                                updatedAt: datetime()
                            })
                            CREATE (javert:Character {
                                id: randomUUID(),
                                projectId: $pid,
                                name: 'Javert',
                                role: 'antagonist',
                                imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Javert',
                                `extras.age`: 50,
                                `extras.gender`: 'Male',
                                `extras.description`: 'A police inspector obsessed with justice.',
                                createdAt: datetime(),
                                updatedAt: datetime()
                            })
                            CREATE (fantine:Character {
                                id: randomUUID(),
                                projectId: $pid,
                                name: 'Fantine',
                                role: 'supporting',
                                imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Fantine',
                                `extras.age`: 26,
                                `extras.gender`: 'Female',
                                `extras.description`: 'A struggling mother who sacrifices everything.',
                                createdAt: datetime(),
                                updatedAt: datetime()
                            })
                            CREATE (cosette:Character {
                                id: randomUUID(),
                                projectId: $pid,
                                name: 'Cosette',
                                role: 'supporting',
                                imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Cosette',
                                `extras.age`: 18,
                                `extras.gender`: 'Female',
                                `extras.description`: 'Fantine’s daughter, adopted by Valjean.',
                                createdAt: datetime(),
                                updatedAt: datetime()
                            })
                            CREATE (marius:Character {
                                id: randomUUID(),
                                projectId: $pid,
                                name: 'Marius Pontmercy',
                                role: 'sidekick',
                                imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Marius',
                                `extras.age`: 20,
                                `extras.gender`: 'Male',
                                `extras.description`: 'A young revolutionary who falls in love with Cosette.',
                                createdAt: datetime(),
                                updatedAt: datetime()
                            })
                            CREATE (thenardier:Character {
                                id: randomUUID(),
                                projectId: $pid,
                                name: 'Thénardier',
                                role: 'other',
                                imageUrl: 'https://api.dicebear.com/7.x/adventurer/svg?seed=Thenardier',
                                `extras.age`: 50,
                                `extras.gender`: 'Male',
                                `extras.description`: 'A greedy and treacherous innkeeper.',
                                createdAt: datetime(),
                                updatedAt: datetime()
                            })

                            // Relationships
                            CREATE (valjean)-[:RELATED_TO {type: 'enemy', weight: -10, description: 'Pursuer'}]->(javert)
                            CREATE (javert)-[:RELATED_TO {type: 'enemy', weight: -10, description: 'Prey'}]->(valjean)

                            CREATE (valjean)-[:RELATED_TO {type: 'friendly', weight: 10, description: 'Adopted Daughter'}]->(cosette)
                            CREATE (cosette)-[:RELATED_TO {type: 'friendly', weight: 10, description: 'Father Figure'}]->(valjean)

                            CREATE (fantine)-[:RELATED_TO {type: 'family', weight: 10, description: 'Daughter'}]->(cosette)

                            CREATE (marius)-[:RELATED_TO {type: 'romantic', weight: 8, description: 'Lover'}]->(cosette)
                            CREATE (cosette)-[:RELATED_TO {type: 'romantic', weight: 8, description: 'Lover'}]->(marius)

                            CREATE (thenardier)-[:RELATED_TO {type: 'hostile', weight: -5, description: 'Exploited'}]->(fantine)
                            CREATE (thenardier)-[:RELATED_TO {type: 'hostile', weight: -5, description: 'Exploited'}]->(cosette)
                            """,
                            java.util.Map.of("pid", realProjectId.toString()));
                    return null;
                });

                log.info("Les Misérables character data seeded successfully.");
            } catch (Exception e) {
                log.error("Failed to seed Neo4j data: {}", e.getMessage(), e);
                // We don't necessarily want to fail the whole transaction if only Neo4j fails,
                // but for seeding it's better to know.
                throw new RuntimeException("Neo4j Seeding Failed", e);
            }

        } catch (Exception e) {
            log.error("Seeding process failed: {}", e.getMessage(), e);
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
}
