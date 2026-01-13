package com.stolink.backend.domain.event.dto;

import java.util.List;
import java.util.UUID;

/**
 * 이벤트 응답 DTO
 */
public record EventResponse(
                UUID id,
                String eventId,
                String name,
                String eventType,
                String description,
                List<String> participants,
                Integer chapter,
                Integer sequenceOrder,
                String narrativeSummary,
                Double importance,
                String locationRef,
                String startTime,
                String endTime,
                UUID documentId,
                UUID projectId) {
}
