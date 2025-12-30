package com.stolink.backend.domain.plot.repository;

import com.stolink.backend.domain.plot.entity.PlotIntegration;
import com.stolink.backend.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlotIntegrationRepository extends JpaRepository<PlotIntegration, UUID> {

    List<PlotIntegration> findByProject(Project project);

    Optional<PlotIntegration> findByProjectAndDocumentId(Project project, UUID documentId);

    void deleteAllByProject(Project project);
}
