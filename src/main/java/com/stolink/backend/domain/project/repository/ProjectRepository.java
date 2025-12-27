package com.stolink.backend.domain.project.repository;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Page<Project> findByUser(User user, Pageable pageable);

    Optional<Project> findByIdAndUser(UUID id, User user);

    Optional<Project> findByTitleAndUser(String title, User user);

    boolean existsByIdAndUser(UUID id, User user);
}
