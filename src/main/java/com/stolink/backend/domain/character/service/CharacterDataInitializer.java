package com.stolink.backend.domain.character.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import java.util.Map;

@Slf4j
// @Component
@RequiredArgsConstructor
@Profile("!test") // Run only in non-test profiles
public class CharacterDataInitializer implements CommandLineRunner {

        private final CharacterRepository characterRepository;
        private final ObjectMapper objectMapper;

        @Override
        public void run(String... args) throws Exception {
                if (characterRepository.count() > 0) {
                        log.info("Character data already exists. Skipping initialization.");
                        return;
                }

                log.info("Initializing dummy character data...");

                String dummyProjectId = "550e8400-e29b-41d4-a716-446655440000";

                // Create Characters
                Character protagonist = Character.builder()
                                .projectId(dummyProjectId)
                                .name("아린")
                                .role("protagonist")
                                .imageUrl("https://api.dicebear.com/7.x/adventurer/svg?seed=Arin")
                                .extrasJson(toJson(
                                                Map.of("age", 20, "species", "Human", "personality", "Brave, Curious")))
                                .build();

                Character antagonist = Character.builder()
                                .projectId(dummyProjectId)
                                .name("말자하")
                                .role("antagonist")
                                .imageUrl("https://api.dicebear.com/7.x/adventurer/svg?seed=Malzaha")
                                .extrasJson(toJson(Map.of("age", 500, "species", "Dark Elf", "power", "Shadow Magic")))
                                .build();

                Character helper = Character.builder()
                                .projectId(dummyProjectId)
                                .name("루나")
                                .role("supporting")
                                .imageUrl("https://api.dicebear.com/7.x/adventurer/svg?seed=Luna")
                                .extrasJson(toJson(Map.of("age", 100, "species", "Spirit")))
                                .build();

                // Save characters first to generate IDs
                protagonist = characterRepository.save(protagonist);
                antagonist = characterRepository.save(antagonist);
                helper = characterRepository.save(helper);

                // Create Relationships
                characterRepository.createRelationship(protagonist.getId(), helper.getId(), "ally", 5,
                                "Trusted companion");
                characterRepository.createRelationship(protagonist.getId(), antagonist.getId(), "enemy", -5,
                                "Destined rival");
                characterRepository.createRelationship(antagonist.getId(), protagonist.getId(), "enemy", -5,
                                "Obstacle to power");

                log.info("Dummy character data initialized successfully.");
        }

        private String toJson(Map<String, Object> map) {
                try {
                        return objectMapper.writeValueAsString(map);
                } catch (JsonProcessingException e) {
                        log.error("Failed to serialize to JSON", e);
                        return "{}";
                }
        }
}
