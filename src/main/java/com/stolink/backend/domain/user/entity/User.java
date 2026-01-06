package com.stolink.backend.domain.user.entity;

import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = true) // OAuth2 사용자는 null 가능
    private String password;

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column
    private String providerId; // OAuth2 provider's user ID (e.g., Google sub)

    // Notification Settings
    @Column(nullable = false)
    @Builder.Default
    private boolean goalNotification = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean foreshadowingNotification = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean aiSuggestionNotification = true;

    public void updateProfile(String nickname, String avatarUrl) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (avatarUrl != null) {
            this.avatarUrl = avatarUrl;
        }
    }

    public void updateNotificationSettings(boolean goalNotification, boolean foreshadowingNotification, boolean aiSuggestionNotification) {
        this.goalNotification = goalNotification;
        this.foreshadowingNotification = foreshadowingNotification;
        this.aiSuggestionNotification = aiSuggestionNotification;
    }

    public void updateProviderId(String providerId) {
        this.providerId = providerId;
    }
}
