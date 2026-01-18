package com.stolink.backend.domain.event.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.repository.CharacterRepository;
import com.stolink.backend.domain.event.dto.EventResponse;
import com.stolink.backend.domain.event.node.Event;
import com.stolink.backend.domain.event.repository.EventNeo4jRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이벤트 조회 서비스 (Neo4j 기반)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventNeo4jRepository eventNeo4jRepository;
    private final CharacterRepository characterRepository; // Neo4j Repository
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    /**
     * 캐릭터가 참여한 이벤트 목록 조회
     */
    public List<EventResponse> getEventsByCharacter(UUID userId, UUID characterId) {
        log.info("Fetching events for characterId: {}, userId: {}", characterId, userId);

        // 1. Neo4j에서 캐릭터 조회 (id 또는 characterId로 조회 시도)
        Character character = characterRepository.findById(characterId.toString())
                .or(() -> characterRepository.findByCharacterId(characterId.toString()))
                .orElseThrow(() -> {
                    log.error("Character not found in Neo4j: {}", characterId);
                    return new ResourceNotFoundException("Character not found: " + characterId);
                });

        // 2. 프로젝트 조회 및 소유권 검증
        UUID projectId;
        try {
            String projectIdStr = character.getProjectId();
            if (projectIdStr == null) {
                log.error("Character {} (id: {}) has no associated projectId/project_id in Neo4j", character.getName(),
                        characterId);
                throw new ResourceNotFoundException("Project ID not found for character: " + characterId);
            }
            projectId = UUID.fromString(projectIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for projectId in character {}: {}", characterId, character.getProjectId());
            throw new ResourceNotFoundException("Invalid Project ID in Character node");
        }

        Project project = projectRepository.findByIdWithUser(projectId)
                .orElseThrow(() -> {
                    log.error("Project not found: {}", projectId);
                    return new ResourceNotFoundException("Project not found: " + projectId);
                });

        if (!project.getUser().getId().equals(userId)) {
            log.error("Project access denied. Project Owner: {}, Requester: {}", project.getUser().getId(), userId);
            throw new ResourceNotFoundException("Project not found");
        }

        // 3. relationsJson에서 event_refs 파싱
        List<String> eventRefs = parseEventRefsFromRelationsJson(character.getRelationsJson());
        log.info("Parsed event_refs for character {}: {}", character.getName(), eventRefs);

        if (eventRefs.isEmpty()) {
            log.info("No event_refs found for character {}", character.getName());
            return List.of();
        }

        // 4. Neo4j에서 직접 eventId 리스트로 필터링 (최적화)
        List<Event> events = eventNeo4jRepository.findEventsByProjectIdAndEventRefs(
                projectId.toString(), eventRefs);

        log.info("Found {} events for character {} (using optimized query)",
                events.size(), character.getName());

        return events.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * relationsJson에서 event_refs 배열 추출 (snake_case/camelCase 모두 지원)
     */
    private List<String> parseEventRefsFromRelationsJson(String relationsJson) {
        if (relationsJson == null || relationsJson.isBlank()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(relationsJson);

            // snake_case 또는 camelCase 키 모두 체크
            com.fasterxml.jackson.databind.JsonNode eventRefsNode = root.get("event_refs");
            if (eventRefsNode == null) {
                eventRefsNode = root.get("eventRefs");
            }

            if (eventRefsNode == null || !eventRefsNode.isArray()) {
                return List.of();
            }
            List<String> refs = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode node : eventRefsNode) {
                refs.add(node.asText());
            }
            return refs;
        } catch (Exception e) {
            log.warn("Failed to parse relationsJson: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 프로젝트의 전체 이벤트 목록 조회
     */
    @Transactional(readOnly = true)
    public List<EventResponse> getEventsByProject(UUID userId, UUID projectId) {
        User user = getUserOrThrow(userId);
        getProjectOrThrow(projectId, user);

        List<Event> events = eventNeo4jRepository.findByProjectId(projectId.toString());
        return events.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private EventResponse toResponse(Event node) {
        // Node ID is String, DTO expects UUID. If Node ID is not UUID format, generate
        // one or handle error.
        // Assuming Node ID generated by UUIDStringGenerator is a valid UUID string.
        UUID id = null;
        try {
            if (node.getId() != null) {
                id = UUID.fromString(node.getId());
            }
        } catch (IllegalArgumentException e) {
            // If ID is not UUID, leave null or generate random (though persistent ID
            // preferred)
            log.warn("Node ID is not UUID format: {}", node.getId());
        }

        UUID documentId = null;
        try {
            if (node.getDocumentId() != null) {
                documentId = UUID.fromString(node.getDocumentId());
            }
        } catch (IllegalArgumentException e) {
            log.warn("Document ID in node is not UUID format: {}", node.getDocumentId());
        }

        UUID projectId = null;
        try {
            if (node.getProjectId() != null) {
                projectId = UUID.fromString(node.getProjectId());
            }
        } catch (IllegalArgumentException e) {
            log.warn("Project ID in node is not UUID format: {}", node.getProjectId());
        }

        // 참여자 목록 구성: DB 속성(participants) + 그래프 관계(participantNodes) 합치기
        List<String> combinedParticipants = new ArrayList<>();
        if (node.getParticipants() != null) {
            combinedParticipants.addAll(node.getParticipants());
        }
        if (node.getParticipantNodes() != null) {
            node.getParticipantNodes().forEach(p -> {
                if (!combinedParticipants.contains(p.getName())) {
                    combinedParticipants.add(p.getName());
                }
            });
        }
        if (node.getParticipantNodesLegacy() != null) {
            node.getParticipantNodesLegacy().forEach(p -> {
                if (!combinedParticipants.contains(p.getName())) {
                    combinedParticipants.add(p.getName());
                }
            });
        }

        return new EventResponse(
                id,
                node.getEventId(),
                node.getNarrativeSummary(),
                node.getEventType(),
                node.getDescription(),
                combinedParticipants,
                node.getChapter(),
                node.getSequenceOrder(),
                node.getNarrativeSummary(),
                node.getImportance() != null ? node.getImportance().doubleValue() : null,
                node.getLocationRef(),
                null, // StartTime
                null, // EndTime
                documentId,
                projectId);
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private void getProjectOrThrow(UUID projectId, User user) {
        projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
    }
}
