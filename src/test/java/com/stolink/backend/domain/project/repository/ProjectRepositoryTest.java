package com.stolink.backend.domain.project.repository;

import com.stolink.backend.domain.project.entity.Project;
// Imports removed
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
class ProjectRepositoryTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private com.stolink.backend.domain.user.repository.UserRepository userRepository;

    @Test
    @DisplayName("Save project with large cover image")
    void saveProjectWithLargeCoverImage() {
        // Create and save a user first
        com.stolink.backend.domain.user.entity.User user = com.stolink.backend.domain.user.entity.User.builder()
                .email("test@example.com")
                .nickname("testuser")
                .build();
        user = userRepository.save(user);

        // Generate a large string (approx 5MB)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5 * 1024 * 1024; i++) {
            sb.append("a");
        }
        String largeCoverImage = sb.toString();

        Project project = Project.builder()
                .title("Test Project")
                .description("Test Description")
                .genre(Project.Genre.FANTASY)
                .status(Project.ProjectStatus.WRITING)
                .coverImage(largeCoverImage)
                .user(user)
                .build();

        Project savedProject = projectRepository.save(project);

        assertThat(savedProject.getId()).isNotNull();
        assertThat(savedProject.getCoverImage()).isEqualTo(largeCoverImage);
    }
}
