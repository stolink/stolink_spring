package com.stolink.backend.domain.character.dto;

import lombok.Builder;
import java.util.List;

@Builder
public record RelationshipResponse(
        String id,
        String sourceId,
        String targetId,
        List<String> types,
        Integer strength,
        String description,
        Boolean bidirectional) {
}
