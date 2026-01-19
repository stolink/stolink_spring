package com.stolink.backend.domain.character.repository;

import java.util.List;

public interface RelationshipProjection {
        String getSourceId();

        String getTargetId();

        Long getRelId();

        List<String> getTypes();

        Integer getStrength();

        String getDescription();

        Boolean getBidirectional();

        String getSince();

        String getProjectId();
}
