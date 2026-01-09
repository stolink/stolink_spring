package com.stolink.backend.debug;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.character.entity.CharacterEntity;
import com.stolink.backend.domain.character.repository.CharacterJpaRepository;
import com.stolink.backend.domain.ai.service.AICallbackService;
import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@SpringBootTest
@ActiveProfiles("local")
@org.springframework.test.annotation.Rollback(false)
public class DbCheckerTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private CharacterJpaRepository characterJpaRepository;

    @Autowired
    private AICallbackService aiCallbackService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void triggerInjection() throws Exception {
        File file = new File("dummy_data.json");
        AnalysisCallbackDTO callback = objectMapper.readValue(file, AnalysisCallbackDTO.class);

        System.out.println("Triggering callback for job: " + callback.getJobId());
        System.out.println("Character count in callback: " +
            (callback.getEffectiveCharacters() != null ? callback.getEffectiveCharacters().size() : "NULL"));
        aiCallbackService.handleAnalysisCallback(callback);
        System.out.println("Callback triggered successfully.");

        System.out.println("Global character count: " + characterJpaRepository.count());
    }

    @Autowired
    private com.stolink.backend.domain.character.repository.CharacterRepository neo4jCharacterRepository;

    @Test
    public void checkDb() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== PROJECT LIST ===\n");
        List<Project> projects = projectRepository.findAll();
        for (Project p : projects) {
            sb.append("Project: ").append(p.getId()).append(" | Title: ").append(p.getTitle()).append("\n");
        }

        sb.append("=== CHARACTER COUNT PER PROJECT ===\n");
        for (Project p : projects) {
            String pid = p.getId().toString();
            List<CharacterEntity> pgChars = characterJpaRepository.findAll().stream()
                .filter(c -> c.getProject().getId().equals(p.getId()))
                .toList();

            List<com.stolink.backend.domain.character.node.Character> neoChars = neo4jCharacterRepository.findByProjectId(pid);

            sb.append("Project ").append(p.getTitle()).append(" (").append(pid).append("):\n");
            sb.append("  Postgres: ").append(pgChars.size()).append(" characters\n");
            sb.append("  Neo4j:    ").append(neoChars.size()).append(" characters\n");

            if (!pgChars.isEmpty()) {
                sb.append("  Names (PG, first 10): ").append(pgChars.stream().limit(10).map(c -> c.getName()).collect(Collectors.toList())).append("\n");
            }
        }
        System.out.println(sb.toString());
    }
}
