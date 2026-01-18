package com.stolink.backend.domain.project.service;

import com.stolink.backend.domain.ai.repository.CallbackLogRepository;
import com.stolink.backend.domain.ai.repository.CharacterTimelineRepository;
import com.stolink.backend.domain.ai.repository.DocumentSummaryRepository;
import com.stolink.backend.domain.character.repository.ImageGenerationTaskRepository;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.document.service.DocumentService;
import com.stolink.backend.domain.draft.repository.DraftRepository;
import com.stolink.backend.domain.project.dto.CreateProjectRequest;
import com.stolink.backend.domain.project.dto.ProjectResponse;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @InjectMocks
    private ProjectService projectService;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentService documentService;
    @Mock
    private DocumentSummaryRepository documentSummaryRepository;
    @Mock
    private CharacterTimelineRepository characterTimelineRepository;
    @Mock
    private ImageGenerationTaskRepository imageGenerationTaskRepository;
    @Mock
    private CallbackLogRepository callbackLogRepository;
    @Mock
    private DraftRepository draftRepository;

    @Test
    @DisplayName("Create project with cover image")
    void createProjectWithCoverImage() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@test.com").build();
        CreateProjectRequest request = CreateProjectRequest.builder()
                .title("Test Project")
                .description("Description")
                .coverImage("http://example.com/cover.jpg")
                .build();
        
        Project savedProject = Project.builder()
                .user(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .coverImage(request.getCoverImage())
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

        // When
        ProjectResponse response = projectService.createProject(userId, request);

        // Then
        assertThat(response.getCoverImage()).isEqualTo("http://example.com/cover.jpg");
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    @DisplayName("Update project with cover image")
    void updateProjectWithCoverImage() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@test.com").build();
        Project project = Project.builder()
                .id(projectId)
                .user(user)
                .title("Old Title")
                .build();

        CreateProjectRequest request = CreateProjectRequest.builder()
                .title("New Title")
                .coverImage("http://example.com/new-cover.jpg")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(projectRepository.findByIdAndUser(projectId, user)).thenReturn(Optional.of(project));

        // When
        ProjectResponse response = projectService.updateProject(userId, projectId, request);

        // Then
        assertThat(response.getCoverImage()).isEqualTo("http://example.com/new-cover.jpg");
        assertThat(project.getCoverImage()).isEqualTo("http://example.com/new-cover.jpg");
    }
}
