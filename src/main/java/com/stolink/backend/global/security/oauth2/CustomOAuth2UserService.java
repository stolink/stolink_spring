package com.stolink.backend.global.security.oauth2;

import com.stolink.backend.domain.user.entity.AuthProvider;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * OAuth2 사용자 정보 처리 서비스
 *
 * Google OAuth2 인증 후 사용자 정보를 처리합니다.
 * - 기존 사용자: 로그인 처리
 * - 신규 사용자: 자동 회원가입
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        if ("google".equals(registrationId)) {
            return processGoogleUser(oAuth2User);
        }

        throw new OAuth2AuthenticationException("Unsupported OAuth2 provider: " + registrationId);
    }

    private OAuth2User processGoogleUser(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String googleId = (String) attributes.get("sub");
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String picture = (String) attributes.get("picture");

        log.info("Processing Google OAuth2 user: email={}, googleId={}", email, googleId);

        // 기존 사용자 조회 (Google ID 또는 이메일로)
        Optional<User> existingUser = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, googleId);

        User user;
        if (existingUser.isPresent()) {
            // 기존 Google 사용자: 로그인
            user = existingUser.get();
            log.info("Existing Google user logged in: {}", email);
        } else {
            // 이메일로 기존 사용자 확인
            Optional<User> userByEmail = userRepository.findByEmail(email);
            if (userByEmail.isPresent()) {
                User localUser = userByEmail.get();
                if (localUser.getProvider() == AuthProvider.LOCAL) {
                    // 기존 LOCAL 계정이 있는 경우 - Google 계정 연결
                    log.warn("Email {} already registered with LOCAL provider. Consider account linking.", email);
                    throw new OAuth2AuthenticationException(
                            "이미 일반 회원가입으로 등록된 이메일입니다. 기존 계정으로 로그인해주세요.");
                }
                user = localUser;
            } else {
                // 신규 사용자: 자동 회원가입
                user = User.builder()
                        .email(email)
                        .nickname(name != null ? name : email.split("@")[0])
                        .avatarUrl(picture)
                        .provider(AuthProvider.GOOGLE)
                        .providerId(googleId)
                        .build();
                user = userRepository.save(user);
                log.info("New Google user registered: {}", email);
            }
        }

        // userId를 attributes에 추가하여 SuccessHandler에서 사용
        Map<String, Object> modifiedAttributes = new java.util.HashMap<>(attributes);
        modifiedAttributes.put("userId", user.getId().toString());

        return new DefaultOAuth2User(
                Collections.emptyList(),
                modifiedAttributes,
                "email");
    }
}
