package com.stolink.backend.domain.project.service;

import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.project.dto.CreateProjectRequest;
import com.stolink.backend.domain.project.dto.ProjectResponse;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    public Page<ProjectResponse> getProjects(UUID userId, Pageable pageable) {
        User user = getUserOrThrow(userId);

        return projectRepository.findByUser(user, pageable)
                .map(project -> {
                    ProjectResponse.ProjectStats stats = getProjectStats(project);
                    return ProjectResponse.from(project, stats);
                });
    }

    @Transactional
    public ProjectResponse createProject(UUID userId, CreateProjectRequest request) {
        User user = getUserOrThrow(userId);

        Project project = Project.builder()
                .user(user)
                .title(request.getTitle())
                .genre(request.getGenreEnum())
                .description(request.getDescription())
                .status(Project.ProjectStatus.WRITING)
                .build();

        project = projectRepository.save(project);
        log.info("Project created: {} by user: {}", project.getId(), userId);

        return ProjectResponse.from(project);
    }

    public ProjectResponse getProject(UUID userId, UUID projectId) {
        User user = getUserOrThrow(userId);
        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        ProjectResponse.ProjectStats stats = getProjectStats(project);
        return ProjectResponse.from(project, stats);
    }

    @Transactional
    public ProjectResponse updateProject(UUID userId, UUID projectId, CreateProjectRequest request) {
        User user = getUserOrThrow(userId);
        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        project.update(
                request.getTitle(),
                request.getGenreEnum(),
                request.getDescription(),
                null,
                null);

        return ProjectResponse.from(project);
    }

    @Transactional
    public void deleteProject(UUID userId, UUID projectId) {
        User user = getUserOrThrow(userId);
        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        projectRepository.delete(project);
        log.info("Project deleted: {}", projectId);
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private ProjectResponse.ProjectStats getProjectStats(Project project) {
        Long totalWords = documentRepository.sumWordCountByProject(project);
        Long chapterCount = documentRepository.countTextDocumentsByProject(project);

        return ProjectResponse.ProjectStats.builder()
                .totalWords(totalWords != null ? totalWords : 0L)
                .chapterCount(chapterCount != null ? chapterCount : 0L)
                .build();
    }
}
