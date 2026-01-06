package com.stolink.backend.domain.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter // for deserialization
@NoArgsConstructor
public class UpdateNotificationSettingsRequest {
    private Boolean goalNotification;
    private Boolean foreshadowingNotification;
    private Boolean aiSuggestionNotification;
}
