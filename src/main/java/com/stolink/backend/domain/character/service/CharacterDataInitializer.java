package com.stolink.backend.domain.character.service;

import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;

@Slf4j
// @Component
@RequiredArgsConstructor
@Profile("!test") // Run only in non-test profiles
public class CharacterDataInitializer implements CommandLineRunner {

        private final CharacterRepository characterRepository;

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
                                .build();
                protagonist.updateExtras("age", 20);
                protagonist.updateExtras("species", "Human");
                protagonist.updateExtras("personality", "Brave, Curious");

                Character antagonist = Character.builder()
                                .projectId(dummyProjectId)
                                .name("말자하")
                                .role("antagonist")
                                .imageUrl("https://api.dicebear.com/7.x/adventurer/svg?seed=Malzaha")
                                .build();
                antagonist.updateExtras("age", 500);
                antagonist.updateExtras("species", "Dark Elf");
                antagonist.updateExtras("power", "Shadow Magic");

                Character helper = Character.builder()
                                .projectId(dummyProjectId)
                                .name("루나")
                                .role("supporting")
                                .imageUrl("https://api.dicebear.com/7.x/adventurer/svg?seed=Luna")
                                .build();
                helper.updateExtras("age", 100);
                helper.updateExtras("species", "Spirit");

                // Save characters first to generate IDs
                protagonist = characterRepository.save(protagonist);
                antagonist = characterRepository.save(antagonist);
                helper = characterRepository.save(helper);

                // Create Relationships
                // Protagonist -> Helper (Friend)
                // Note: In Neo4j SDN, we might need to rely on the service to create
                // relationships with properties properly
                // or re-save the entity with relationship objects added.
                // For simplicity in this dummy loader, we can use the repository's custom query
                // or just relationship objects if simpler.
                // Let's use the repository method for consistency with service.

                characterRepository.createRelationship(protagonist.getId(), helper.getId(), "ally", 5,
                                "Trusted companion");
                characterRepository.createRelationship(protagonist.getId(), antagonist.getId(), "enemy", -5,
                                "Destined rival");
                characterRepository.createRelationship(antagonist.getId(), protagonist.getId(), "enemy", -5,
                                "Obstacle to power");

                log.info("Dummy character data initialized successfully.");
        }
}
