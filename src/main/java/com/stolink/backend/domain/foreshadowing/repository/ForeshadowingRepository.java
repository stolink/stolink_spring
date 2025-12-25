package com.stolink.backend.domain.foreshadowing.repository;

import com.stolink.backend.domain.foreshadowing.entity.Foreshadowing;
import com.stolink.backend.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForeshadowingRepository extends JpaRepository<Foreshadowing, UUID> {

    List<Foreshadowing> findByProject(Project project);

    List<Foreshadowing> findByProjectAndStatus(Project project, Foreshadowing.ForeshadowingStatus status);

    Optional<Foreshadowing> findByIdAndProject(UUID id, Project project);

    Optional<Foreshadowing> findByProjectAndTag(Project project, String tag);

    boolean existsByProjectAndTag(Project project, String tag);
}
