package com.stolink.backend.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.ai.dto.callback.EventDTO;
import com.stolink.backend.domain.event.entity.EventEntity;
import com.stolink.backend.domain.event.repository.EventJpaRepository;
import com.stolink.backend.domain.project.entity.Project;

@ExtendWith(MockitoExtension.class)
class EventDeduplicationServiceTest {

    @Mock
    private EventJpaRepository eventJpaRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EventDeduplicationService eventDeduplicationService;

    private Project project;
    private final ObjectMapper realMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(UUID.randomUUID())
                .title("Test Project")
                .build();
    }

    @Test
    @DisplayName("챕터가 없으면 중복검사 건너뜀")
    void skipIfChapterMissing() {
        EventDTO newEvent = EventDTO.builder()
                .eventId("evt_new")
                .chapter(null)
                .build();

        Optional<EventEntity> result = eventDeduplicationService.findDuplicateEvent(project, newEvent);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Participants 유사도가 높으면 중복으로 판정 (Jaccard > 0.8)")
    void duplicateByParticipants() throws Exception {
        // Given
        int chapter = 1;
        List<String> existingParticipants = List.of("Alice", "Bob", "Charlie");
        List<String> newParticipants = List.of("Alice", "Bob", "Charlie", "David"); // 3/4 = 0.75 (low) -> 3/4 overlap?
        // Jaccard: Intersection(3) / Union(4) = 0.75 < 0.8 (Fail)
        // Let's try stronger match
        List<String> newParticipants2 = List.of("Alice", "Bob", "Charlie"); // 3/3 = 1.0 > 0.8 (Pass)

        EventEntity existing = EventEntity.builder()
                .eventId("evt_existing")
                .chapter(chapter)
                .participants("[\"Alice\", \"Bob\", \"Charlie\"]")
                .build();

        EventDTO newEvent = EventDTO.builder()
                .eventId("evt_new")
                .chapter(chapter)
                .participants(newParticipants2)
                .build();

        when(eventJpaRepository.findAllByProjectAndChapter(any(), eq(chapter)))
                .thenReturn(List.of(existing));

        // Mocking real mapper behavior
        when(objectMapper.getTypeFactory()).thenReturn(realMapper.getTypeFactory());
        when(objectMapper.readValue(eq(existing.getParticipants()), any(com.fasterxml.jackson.databind.JavaType.class)))
                .thenReturn(existingParticipants);

        // When
        Optional<EventEntity> result = eventDeduplicationService.findDuplicateEvent(project, newEvent);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getEventId()).isEqualTo("evt_existing");
    }

    @Test
    @DisplayName("Embedding 유사도가 높으면 중복으로 판정 (Cosine > 0.85)")
    void duplicateByEmbedding() throws Exception {
        // Given
        int chapter = 2;
        List<Double> vec1 = List.of(1.0, 0.0, 0.0);
        List<Double> vec2 = List.of(0.99, 0.05, 0.0); // Very close

        EventEntity existing = EventEntity.builder()
                .eventId("evt_existing_vec")
                .chapter(chapter)
                .embeddingJson("[1.0, 0.0, 0.0]")
                .build();

        EventDTO newEvent = EventDTO.builder()
                .eventId("evt_new_vec")
                .chapter(chapter)
                .embedding(vec2)
                .build();

        when(eventJpaRepository.findAllByProjectAndChapter(any(), eq(chapter)))
                .thenReturn(List.of(existing));

        when(objectMapper.getTypeFactory()).thenReturn(realMapper.getTypeFactory());
        when(objectMapper.readValue(eq(existing.getEmbeddingJson()), any(com.fasterxml.jackson.databind.JavaType.class)))
                .thenReturn(vec1);

        // When
        Optional<EventEntity> result = eventDeduplicationService.findDuplicateEvent(project, newEvent);

        // Then
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("유사도가 모두 낮으면 중복 아님")
    void notDuplicateIfLowSimilarity() throws Exception {
        // Given
        int chapter = 3;
        List<String> p1 = List.of("Alice");
        List<String> p2 = List.of("Zoro"); // Jaccard 0

        List<Double> v1 = List.of(1.0, 0.0);
        List<Double> v2 = List.of(0.0, 1.0); // Cosine 0

        EventEntity existing = EventEntity.builder()
                .eventId("evt_diff")
                .chapter(chapter)
                .participants("[\"Alice\"]")
                .embeddingJson("[1.0, 0.0]")
                .narrativeSummary("Alice eats apple")
                .build();

        EventDTO newEvent = EventDTO.builder()
                .eventId("evt_new_diff")
                .chapter(chapter)
                .participants(p2)
                .embedding(v2)
                .narrativeSummary("Zoro using sword")
                .build();

        when(eventJpaRepository.findAllByProjectAndChapter(any(), eq(chapter)))
                .thenReturn(List.of(existing));

        when(objectMapper.getTypeFactory()).thenReturn(realMapper.getTypeFactory());
        when(objectMapper.readValue(eq(existing.getParticipants()), any(com.fasterxml.jackson.databind.JavaType.class)))
                .thenReturn(p1);
        when(objectMapper.readValue(eq(existing.getEmbeddingJson()), any(com.fasterxml.jackson.databind.JavaType.class)))
                .thenReturn(v1);

        // When
        Optional<EventEntity> result = eventDeduplicationService.findDuplicateEvent(project, newEvent);

        // Then
        assertThat(result).isEmpty();
    }
}
