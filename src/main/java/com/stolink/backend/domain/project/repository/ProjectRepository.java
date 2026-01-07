package com.stolink.backend.domain.project.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.user.entity.User;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Page<Project> findByUser(User user, Pageable pageable);

    Optional<Project> findByIdAndUser(UUID id, User user);

    Optional<Project> findByTitleAndUser(String title, User user);

    boolean existsByIdAndUser(UUID id, User user);

    @Query("SELECT p FROM Project p JOIN FETCH p.user WHERE p.id = :id")
    Optional<Project> findByIdWithUser(@Param("id") UUID id);

    List<Project> findByUser(User user);
}
