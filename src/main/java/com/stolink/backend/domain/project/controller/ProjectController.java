package com.stolink.backend.domain.project.controller;

import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.dto.ProjectAnalysisJobResponse;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.project.dto.CreateProjectRequest;
import com.stolink.backend.domain.project.dto.ProjectResponse;
import com.stolink.backend.domain.project.dto.ProjectStatsResponse;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.project.service.ProjectService;
import com.stolink.backend.domain.project.service.ProjectStatsService;
import com.stolink.backend.global.common.dto.ApiResponse;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectStatsService projectStatsService;
    private final ProjectRepository projectRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final DocumentRepository documentRepository;

    @GetMapping
    public ApiResponse<Map<String, Object>> getProjects(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String order) {
        // [DEBUG] 사용자 ID 로깅
        org.slf4j.LoggerFactory.getLogger(ProjectController.class).info("GET /api/projects called by user: {}", userId);
        Sort.Direction direction = order.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(direction, sort));

        Page<ProjectResponse> projects = projectService.getProjects(userId, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("projects", projects.getContent());
        response.put("pagination", Map.of(
                "page", page,
                "limit", limit,
                "total", projects.getTotalElements(),
                "totalPages", projects.getTotalPages()));

        return ApiResponse.ok(response);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> createProject(
            @AuthenticationPrincipal UUID userId,
            @RequestBody CreateProjectRequest request) {
        ProjectResponse project = projectService.createProject(userId, request);
        return ApiResponse.created(project);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> getProject(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        ProjectResponse project = projectService.getProject(userId, id);
        return ApiResponse.ok(project);
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProjectResponse> updateProject(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody CreateProjectRequest request) {
        ProjectResponse project = projectService.updateProject(userId, id, request);
        return ApiResponse.ok(project);
    }

    @GetMapping("/{id}/stats")
    public ApiResponse<ProjectStatsResponse> getProjectStats(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID projectId) {
        ProjectStatsResponse stats = projectStatsService.calculateStats(userId, projectId);
        return ApiResponse.ok(stats);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        projectService.deleteProject(userId, id);
    }

    /**
     * 프로젝트별 분석 작업 상태 조회
     */
    @GetMapping("/{projectId}/analysis/job")
    public ApiResponse<ProjectAnalysisJobResponse> getProjectAnalysisStatus(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID userId) {

        log.info("Get Project Analysis Status request for: {} by user: {}", projectId, userId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        // 해당 프로젝트의 최근 작업들 조회 (AnalysisJobRepository에 정의되어 있어야 함)
        List<AnalysisJob> jobs = analysisJobRepository.findByProjectOrderByCreatedAtDesc(project);

        if (jobs.isEmpty()) {
            return ApiResponse.ok(new ProjectAnalysisJobResponse(null, null, 0, null));
        }

        // 1. 현재 진행 중인 작업이 있는지 확인
        Optional<AnalysisJob> activeJob = jobs.stream()
                .filter(j -> j.getStatus() != AnalysisJob.JobStatus.COMPLETED
                        && j.getStatus() != AnalysisJob.JobStatus.FAILED)
                .findFirst();

        if (activeJob.isPresent()) {
            AnalysisJob job = activeJob.get();
            long total = documentRepository.countTextDocumentsByProjectId(projectId);
            long completedCount = documentRepository.countByProjectIdAndTypeTextAndAnalysisStatus(
                    projectId,
                    com.stolink.backend.domain.document.entity.Document.AnalysisStatus.COMPLETED);

            int progress = total > 0 ? (int) (completedCount * 100 / total) : 0;

            return ApiResponse.ok(new ProjectAnalysisJobResponse(
                    job.getJobId(),
                    "processing",
                    progress,
                    null));
        }

        // 2. 진행 중인 작업이 없으면 가장 최근 상태 반환
        AnalysisJob latestJob = jobs.get(0);

        if (latestJob.getStatus() == AnalysisJob.JobStatus.COMPLETED) {
            return ApiResponse.ok(new ProjectAnalysisJobResponse(
                    null,
                    null,
                    100,
                    latestJob.getCompletedAt()));
        }

        return ApiResponse.ok(new ProjectAnalysisJobResponse(
                latestJob.getJobId(),
                "failed",
                0,
                latestJob.getCompletedAt()));
    }
}
