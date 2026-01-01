package com.stolink.backend.domain.event.repository;

import com.stolink.backend.domain.event.entity.Event;
import com.stolink.backend.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByProject(Project project);

    List<Event> findByProjectOrderByImportanceDesc(Project project);

    Optional<Event> findByProjectAndEventId(Project project, String eventId);

    void deleteAllByProject(Project project);
}
