package com.stolink.backend.domain.dialogue.repository;

import com.stolink.backend.domain.dialogue.entity.Dialogue;
import com.stolink.backend.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DialogueRepository extends JpaRepository<Dialogue, UUID> {

    List<Dialogue> findByProject(Project project);

    Optional<Dialogue> findByProjectAndDialogueId(Project project, String dialogueId);

    void deleteAllByProject(Project project);
}
