package com.stolink.backend.domain.project.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stolink.backend.domain.ai.dto.ProjectAnalysisJobResponse;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.ai.service.AIAnalysisService;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.project.dto.ConsistencyReportResponse;
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
        private final AIAnalysisService aiAnalysisService;

        @GetMapping
        public ApiResponse<Map<String, Object>> getProjects(
                        @AuthenticationPrincipal UUID userId,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "20") int limit,
                        @RequestParam(defaultValue = "updatedAt") String sort,
                        @RequestParam(defaultValue = "desc") String order) {
                // [DEBUG] 사용자 ID 로깅
                org.slf4j.LoggerFactory.getLogger(ProjectController.class).info("GET /api/projects called by user: {}",
                                userId);
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
        @GetMapping({ "/{projectId}/analysis/job", "/{projectId}/analysis/job/" })
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

        /**
         * 일관성 분석 결과 조회
         */
        @GetMapping("/{projectId}/consistency-report")
        public ApiResponse<ConsistencyReportResponse> getConsistencyReport(
                        @PathVariable UUID projectId,
                        @AuthenticationPrincipal UUID userId) {

                log.info("Get Consistency Report request for: {} by user: {}", projectId, userId);

                // 1. 프로젝트 존재 여부 확인
                if (!projectRepository.existsById(projectId)) {
                        throw new ResourceNotFoundException("Project", "id", projectId);
                }

                // 2. 서비스 호출
                ConsistencyReportResponse report = aiAnalysisService.getLatestConsistencyReport(projectId);

                if (report == null) {
                        // 분석 작업이 없거나 결과가 없는 경우 204 No Content 또는 빈 객체 반환
                        // 프론트엔드 요구사항에 따라 204 대신 빈 응답 또는 404를 줄 수 있음.
                        // 여기서는 null data를 가진 200 OK로 주거나, ResourceNotFound로 처리할 수 있음.
                        // 요구사항에 "return 404 Not Found or 204 No Content" 라고 되어 있음.
                        throw new ResourceNotFoundException("Consistency Report", "projectId", projectId);
                }

                return ApiResponse.ok(report);
        }
}
