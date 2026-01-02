package com.stolink.backend.domain.character.dto;

import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.relationship.CharacterRelationship;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterResponseTest {

    @Test
    @DisplayName("Should parse JSON fields into Objects correctly")
    void shouldParseJsonFields() {
        // Given
        Character character = Character.builder()
                .id("1")
                .name("Test Character")
                .aliasesJson("[\"Alias1\", \"Alias2\"]")
                .appearanceJson("{\"physique\": \"athletic\", \"height\": 180}")
                .personalityJson("{\"core_traits\": [\"brave\", \"loyal\"]}")
                .build();

        // When
        CharacterResponse response = CharacterResponse.from(character);

        // Then
        assertThat(response.getName()).isEqualTo("Test Character");

        // Aliases -> List<String>
        assertThat(response.getAliases()).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> aliasesList = (List<String>) response.getAliases();
        assertThat(aliasesList).containsExactly("Alias1", "Alias2");

        // Appearance -> Map
        assertThat(response.getAppearance()).isInstanceOf(Map.class);
        Map<?, ?> appearance = (Map<?, ?>) response.getAppearance();
        assertThat(appearance.get("physique")).isEqualTo("athletic");
        assertThat(appearance.get("height")).isEqualTo(180);

        // Personality -> Map containing List
        assertThat(response.getPersonality()).isInstanceOf(Map.class);
        Map<?, ?> personality = (Map<?, ?>) response.getPersonality();
        assertThat(personality.get("core_traits")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> traitsList = (List<String>) personality.get("core_traits");
        assertThat(traitsList).containsExactly("brave", "loyal");
    }

    @Test
    @DisplayName("Should include source ID and map type in relationships")
    void shouldIncludeSourceIdAndMapType() {
        // Given
        Character target = Character.builder().id("target-1").name("TargetChar").build();

        CharacterRelationship relationship = CharacterRelationship.builder()
                .target(target)
                .type("ALLY")
                .description("Friend")
                .build();

        Character character = Character.builder()
                .id("source-1")
                .name("SourceChar")
                .relationships(List.of(relationship))
                .build();

        // When
        CharacterResponse response = CharacterResponse.from(character);

        // Then
        assertThat(response.getRelationships()).hasSize(1);
        CharacterResponse.CharacterRelationshipResponse relResp = response.getRelationships().get(0);

        // Verify renaming: source -> sourceId, targetCharacterName -> targetId
        assertThat(relResp.getSourceId()).isEqualTo("source-1");
        assertThat(relResp.getTargetId()).isEqualTo("target-1");

        // Verify type mapping: ALLY -> friendly
        assertThat(relResp.getType()).isEqualTo("friendly");
    }

    @Test
    @DisplayName("Should map other relationship types correctly")
    void shouldMapOtherTypes() {
        // Given
        Character target = Character.builder().id("t2").build();
        CharacterRelationship enemyRel = CharacterRelationship.builder().target(target).type("ENEMY").build();
        CharacterRelationship familyRel = CharacterRelationship.builder().target(target).type("FAMILY").build();

        Character character = Character.builder()
                .id("s1")
                .relationships(List.of(enemyRel, familyRel))
                .build();

        // When
        CharacterResponse response = CharacterResponse.from(character);

        // Then
        assertThat(response.getRelationships()).hasSize(2);
        assertThat(response.getRelationships().get(0).getType()).isEqualTo("hostile"); // ENEMY -> hostile
        assertThat(response.getRelationships().get(1).getType()).isEqualTo("family"); // FAMILY -> family
    }

    @Test
    @DisplayName("Should handle invalid JSON gracefully by returning fallback or null")
    void shouldHandleInvalidJson() {
        // Given
        Character character = Character.builder()
                .id("2")
                .name("Broken Json Character")
                .aliasesJson("INVALID JSON")
                .appearanceJson("") // Empty string
                .build();

        // When
        CharacterResponse response = CharacterResponse.from(character);

        // Then
        // Aliases fallback to empty list
        assertThat(response.getAliases()).isInstanceOf(List.class);
        assertThat((List<?>) response.getAliases()).isEmpty();

        // Appearance fallback to null (or empty map depending on safeJsonParse impl, we
        // used null for map fallback)
        assertThat(response.getAppearance()).isNull();
    }

    @Test
    @DisplayName("Should handle null JSON fields")
    void shouldHandleNullJson() {
        // Given
        Character character = Character.builder()
                .id("3")
                .name("Null Json Character")
                .aliasesJson(null)
                .build();

        // When
        CharacterResponse response = CharacterResponse.from(character);

        // Then
        assertThat(response.getAliases()).isInstanceOf(List.class);
        assertThat((List<?>) response.getAliases()).isEmpty();
    }
}
