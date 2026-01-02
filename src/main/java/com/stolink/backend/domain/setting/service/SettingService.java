package com.stolink.backend.domain.setting.service;

import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.setting.dto.SettingResponse;
import com.stolink.backend.domain.setting.repository.SettingNeo4jRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class SettingService {

        private final SettingNeo4jRepository settingNeo4jRepository;
        private final ProjectRepository projectRepository;
        private final com.stolink.backend.domain.user.repository.UserRepository userRepository;

        public List<SettingResponse> getSettingsByProject(UUID userId, UUID projectId) {
                com.stolink.backend.domain.user.entity.User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

                // Verify user owns the project
                projectRepository.findByIdAndUser(projectId, user)
                                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

                return settingNeo4jRepository.findByProjectId(projectId.toString()).stream()
                                .map(SettingResponse::from)
                                .collect(Collectors.toList());
        }
}
