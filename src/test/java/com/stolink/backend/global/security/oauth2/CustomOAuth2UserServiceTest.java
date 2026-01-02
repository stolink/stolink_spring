package com.stolink.backend.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;

import com.stolink.backend.domain.user.entity.AuthProvider;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;

@SpringBootTest
@Transactional
class CustomOAuth2UserServiceTest {

    @Autowired
    private UserRepository userRepository;

    // Helper to test the protected method
    static class TestableCustomOAuth2UserService extends CustomOAuth2UserService {
        public TestableCustomOAuth2UserService(UserRepository userRepository) {
            super(userRepository);
        }

        @Override
        public OAuth2User processGoogleUser(OAuth2User oAuth2User) {
            return super.processGoogleUser(oAuth2User);
        }
    }

    @Test
    @DisplayName("Should login existing Google user and update providerId if necessary")
    void testGoogleUserUpdate() {
        // Given
        UserRepository repo = userRepository;
        TestableCustomOAuth2UserService service = new TestableCustomOAuth2UserService(repo);

        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String email = "test-" + uniqueId + "@example.com";
        String oldProviderId = "old_google_id";
        String newProviderId = "new_google_id";

        // Create existing user with OLD provider ID
        User existingUser = User.builder()
                .email(email)
                .nickname("Test User")
                .provider(AuthProvider.GOOGLE)
                .providerId(oldProviderId)
                .build();
        repo.save(existingUser);
        repo.flush(); // Force flush to detect DB constraints issues immediately

        // Verify setup
        assertThat(repo.findByEmail(email)).isPresent();

        // Prepare OAuth2User input with NEW provider ID
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", newProviderId);
        attributes.put("email", email);
        attributes.put("name", "Test User Updated");
        attributes.put("picture", "http://example.com/pic.jpg");

        OAuth2User inputUser = new DefaultOAuth2User(
                Collections.emptyList(),
                attributes,
                "email");

        // When
        OAuth2User result = service.processGoogleUser(inputUser);

        // Then
        Map<String, Object> resultAttrs = result.getAttributes();
        String userIdStr = (String) resultAttrs.get("userId");
        assertThat(userIdStr).isEqualTo(existingUser.getId().toString());

        // Verify DB update
        User updatedUser = repo.findById(existingUser.getId()).orElseThrow();
        assertThat(updatedUser.getProviderId()).isEqualTo(newProviderId);
    }

    @Test
    @DisplayName("Should create new user if not exists")
    void testNewGoogleUser() {
        // Given
        UserRepository repo = userRepository;
        TestableCustomOAuth2UserService service = new TestableCustomOAuth2UserService(repo);

        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String email = "new-" + uniqueId + "@example.com";
        String providerId = "google_12345";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", providerId);
        attributes.put("email", email);
        attributes.put("name", "New User");

        OAuth2User inputUser = new DefaultOAuth2User(
                Collections.emptyList(),
                attributes,
                "email");

        // When
        OAuth2User result = service.processGoogleUser(inputUser);

        // Then
        String userIdStr = (String) result.getAttributes().get("userId");
        UUID userId = UUID.fromString(userIdStr);

        User savedUser = repo.findById(userId).orElseThrow();
        assertThat(savedUser.getEmail()).isEqualTo(email);
        assertThat(savedUser.getProviderId()).isEqualTo(providerId);
        assertThat(savedUser.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    }
}
