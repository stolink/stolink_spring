package com.stolink.backend.domain.setting.service;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.setting.dto.SettingResponse;
import com.stolink.backend.domain.setting.repository.SettingRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingService {

    private final SettingRepository settingRepository;
    private final ProjectRepository projectRepository;
    private final com.stolink.backend.domain.user.repository.UserRepository userRepository;

    public List<SettingResponse> getSettingsByProject(UUID userId, UUID projectId) {
        com.stolink.backend.domain.user.entity.User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        return settingRepository.findByProject(project).stream()
                .map(SettingResponse::from)
                .collect(Collectors.toList());
    }
}
