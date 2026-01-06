package com.stolink.backend.domain.project.repository;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.entity.WritingGoal;
import com.stolink.backend.domain.project.entity.WritingGoalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WritingGoalRepository extends JpaRepository<WritingGoal, UUID> {
    List<WritingGoal> findByProject(Project project);
    Optional<WritingGoal> findByProjectAndType(Project project, WritingGoalType type);
}
