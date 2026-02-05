package com.stolink.backend.support;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.user.entity.AuthProvider;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class TestDataFactory {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User createUser(String email, String nickname, String password) {
        User user = User.builder()
                .email(email)
                .nickname(nickname)
                .password(passwordEncoder.encode(password))
                .provider(AuthProvider.LOCAL)
                .build();
        return userRepository.save(user);
    }

    public User createDefaultUser() {
        return createUser("test@example.com", "테스트유저", "password123!");
    }

    @Autowired
    private com.stolink.backend.domain.document.repository.DocumentRepository documentRepository;

    public Project createProject(User user, String title) {
        Project project = Project.builder()
                .user(user)
                .title(title)
                .status(Project.ProjectStatus.WRITING)
                .author(user.getNickname())
                .build();
        return projectRepository.save(project);
    }

    public com.stolink.backend.domain.document.entity.Document createDocument(Project project,
            com.stolink.backend.domain.document.entity.Document parent, String title,
            com.stolink.backend.domain.document.entity.Document.DocumentType type) {
        com.stolink.backend.domain.document.entity.Document document = com.stolink.backend.domain.document.entity.Document
                .builder()
                .project(project)
                .parent(parent)
                .title(title)
                .type(type)
                .status(com.stolink.backend.domain.document.entity.Document.DocumentStatus.DRAFT)
                .order(0)
                .wordCount(0)
                .includeInCompile(true)
                .build();
        return documentRepository.save(document);
    }
}
