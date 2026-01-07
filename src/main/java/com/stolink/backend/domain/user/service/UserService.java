package com.stolink.backend.domain.user.service;

import com.stolink.backend.domain.user.dto.NotificationSettingsResponse;
import com.stolink.backend.domain.user.dto.UpdateNotificationSettingsRequest;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public NotificationSettingsResponse getNotificationSettings(UUID userId) {
        User user = getUserOrThrow(userId);
        return NotificationSettingsResponse.from(user);
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(UUID userId, UpdateNotificationSettingsRequest request) {
        User user = getUserOrThrow(userId);

        boolean goalData = request.getGoalNotification() != null ? request.getGoalNotification() : user.isGoalNotification();
        boolean foreshadowingData = request.getForeshadowingNotification() != null ? request.getForeshadowingNotification() : user.isForeshadowingNotification();
        boolean aiData = request.getAiSuggestionNotification() != null ? request.getAiSuggestionNotification() : user.isAiSuggestionNotification();

        user.updateNotificationSettings(goalData, foreshadowingData, aiData);

        return NotificationSettingsResponse.from(user);
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }
}
