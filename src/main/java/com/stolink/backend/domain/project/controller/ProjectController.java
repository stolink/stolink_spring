package com.stolink.backend.domain.project.controller;

import com.stolink.backend.domain.project.dto.CreateProjectRequest;
import com.stolink.backend.domain.project.dto.ProjectResponse;
import com.stolink.backend.domain.project.service.ProjectService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ApiResponse<Map<String, Object>> getProjects(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String order) {
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
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody CreateProjectRequest request) {
        ProjectResponse project = projectService.createProject(userId, request);
        return ApiResponse.created(project);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> getProject(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        ProjectResponse project = projectService.getProject(userId, id);
        return ApiResponse.ok(project);
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProjectResponse> updateProject(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id,
            @RequestBody CreateProjectRequest request) {
        ProjectResponse project = projectService.updateProject(userId, id, request);
        return ApiResponse.ok(project);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        projectService.deleteProject(userId, id);
    }
}
