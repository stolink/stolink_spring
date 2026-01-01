package com.stolink.backend.domain.foreshadowing.repository;

import com.stolink.backend.domain.foreshadowing.entity.Foreshadowing;
import com.stolink.backend.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ForeshadowingRepository extends JpaRepository<Foreshadowing, UUID> {
    Optional<Foreshadowing> findByProjectAndTag(Project project, String tag);
}
