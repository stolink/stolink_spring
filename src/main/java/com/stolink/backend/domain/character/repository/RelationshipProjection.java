package com.stolink.backend.domain.character.repository;

import java.util.List;

public record RelationshipProjection(
        String sourceId,
        String targetId,
        Long relId,
        List<String> types,
        Integer strength,
        String description,
        Boolean bidirectional,
        String since,
        String projectId) {
}
