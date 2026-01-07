package com.stolink.backend.domain.project.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.project.dto.CreateWritingGoalRequest;
import com.stolink.backend.domain.project.dto.WritingGoalResponse;
import com.stolink.backend.domain.project.dto.WritingGoalsResponse;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.entity.WritingGoal;
import com.stolink.backend.domain.project.entity.WritingGoalType;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.project.repository.WritingGoalRepository;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WritingGoalService {

    private final WritingGoalRepository writingGoalRepository;
    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional
    public WritingGoalResponse upsertGoal(UUID userId, UUID projectId, CreateWritingGoalRequest request) {
        Project project = getProjectOrThrow(userId, projectId);

        WritingGoal goal = writingGoalRepository.findByProjectAndType(project, request.getType())
                .orElse(WritingGoal.builder()
                        .project(project)
                        .type(request.getType())
                        .targetCount(request.getTargetCount())
                        .unit(request.getUnit())
                        .build());

        goal.update(request.getTargetCount(), request.getUnit());
        writingGoalRepository.save(goal);

        return mapToResponse(goal, calculateCurrentCount(project, goal.getType()));
    }

    public WritingGoalsResponse getGoals(UUID userId, UUID projectId) {
        Project project = getProjectOrThrow(userId, projectId);

        WritingGoalResponse daily = getGoalResponse(project, WritingGoalType.DAILY);
        WritingGoalResponse weekly = getGoalResponse(project, WritingGoalType.WEEKLY);
        WritingGoalResponse monthly = getGoalResponse(project, WritingGoalType.MONTHLY);

        return WritingGoalsResponse.builder()
                .daily(daily)
                .weekly(weekly)
                .monthly(monthly)
                .build();
    }

    private WritingGoalResponse getGoalResponse(Project project, WritingGoalType type) {
        return writingGoalRepository.findByProjectAndType(project, type)
                .map(goal -> mapToResponse(goal, calculateCurrentCount(project, type)))
                .orElse(null);
    }

    private Long calculateCurrentCount(Project project, WritingGoalType type) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime;

        switch (type) {
            case DAILY:
                startTime = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
                break;
            case WEEKLY:
                // Assuming week starts on Monday
                startTime = LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay();
                break;
            case MONTHLY:
                startTime = LocalDate.now().withDayOfMonth(1).atStartOfDay();
                break;
            default:
                startTime = now;
        }

        // 근사치 계산: 해당 기간에 업데이트된 문서들의 현재 단어 수 합계
        // 정확한 일일 집필량은 별도 History 테이블이 필요함
        return documentRepository.sumWordCountByProjectAndUpdatedAtAfter(project.getId(), startTime);
    }

    private WritingGoalResponse mapToResponse(WritingGoal goal, Long currentCount) {
        return WritingGoalResponse.builder()
                .type(goal.getType())
                .targetCount(goal.getTargetCount())
                .unit(goal.getUnit())
                .currentCount(currentCount)
                .isAchieved(currentCount >= goal.getTargetCount())
                .build();
    }

    private Project getProjectOrThrow(UUID userId, UUID projectId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }
}
