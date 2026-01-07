package com.stolink.backend.domain.event.service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.repository.CharacterRepository;
import com.stolink.backend.domain.event.dto.EventResponse;
import com.stolink.backend.domain.event.entity.EventEntity;
import com.stolink.backend.domain.event.repository.EventJpaRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이벤트 조회 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventJpaRepository eventJpaRepository;
    private final CharacterRepository characterRepository; // Neo4j Repository
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * 캐릭터가 참여한 이벤트 목록 조회
     */
    public List<EventResponse> getEventsByCharacter(UUID userId, UUID characterId) {
        log.info("Fetching events for characterId: {}, userId: {}", characterId, userId);

        // 1. Neo4j에서 캐릭터 조회
        Character character = characterRepository.findById(characterId.toString())
                .orElseThrow(() -> {
                    log.error("Character not found in Neo4j: {}", characterId);
                    return new ResourceNotFoundException("Character not found: " + characterId);
                });

        // 2. 프로젝트 조회 및 소유권 검증 (Lazy Loading 방지 및 명시적 체크)
        UUID projectId = UUID.fromString(character.getProjectId());

        // Eager fetch user using findByIdWithUser
        Project project = projectRepository.findByIdWithUser(projectId)
                .orElseThrow(() -> {
                    log.error("Project not found: {}", projectId);
                    return new ResourceNotFoundException("Project not found: " + projectId);
                });

        if (!project.getUser().getId().equals(userId)) {
             log.error("Project access denied. Project Owner: {}, Requester: {}", project.getUser().getId(), userId);
             throw new ResourceNotFoundException("Project not found");
        }

        // 3. 필터링
        String characterName = character.getName();
        List<EventEntity> events = eventJpaRepository.findByProject(project);

        log.info("Found {} events for project {}, filtering by character '{}'",
                events.size(), projectId, characterName);

        return events.stream()
                .filter(event -> containsParticipant(event.getParticipants(), characterName))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 프로젝트의 전체 이벤트 목록 조회
     */
    @Transactional(readOnly = true)
    public List<EventResponse> getEventsByProject(UUID userId, UUID projectId) {
        User user = getUserOrThrow(userId);
        Project project = getProjectOrThrow(projectId, user);

        List<EventEntity> events = eventJpaRepository.findByProject(project);
        return events.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private boolean containsParticipant(String participantsJson, String characterName) {
        if (participantsJson == null || participantsJson.isBlank()) {
            return false;
        }
        try {
            List<String> participants = objectMapper.readValue(participantsJson, new TypeReference<>() {});
            return participants.contains(characterName);
        } catch (Exception e) {
            log.warn("Failed to parse participants JSON: {}", participantsJson);
            return false;
        }
    }

    private EventResponse toResponse(EventEntity entity) {
        List<String> participants = parseParticipants(entity.getParticipants());
        return new EventResponse(
            entity.getId(),
            entity.getEventId(),
            entity.getName(),
            entity.getEventType(),
            entity.getDescription(),
            participants,
            entity.getChapter(),
            entity.getSequenceOrder(),
            entity.getNarrativeSummary(),
            entity.getImportanceScore(),
            entity.getLocation(),
            entity.getStartTime(),
            entity.getEndTime(),
            entity.getDocumentId()
        );
    }

    private List<String> parseParticipants(String participantsJson) {
        if (participantsJson == null || participantsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(participantsJson, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private Project getProjectOrThrow(UUID projectId, User user) {
        return projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
    }
}
