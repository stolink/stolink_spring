package com.stolink.backend.domain.user.dto;

import com.stolink.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationSettingsResponse {
    private boolean goalNotification;
    private boolean foreshadowingNotification;
    private boolean aiSuggestionNotification;

    public static NotificationSettingsResponse from(User user) {
        return NotificationSettingsResponse.builder()
                .goalNotification(user.isGoalNotification())
                .foreshadowingNotification(user.isForeshadowingNotification())
                .aiSuggestionNotification(user.isAiSuggestionNotification())
                .build();
    }
}
