package com.stolink.backend.domain.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.ai.service.AIAnalysisService;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.project.dto.CreateProjectRequest;
import com.stolink.backend.domain.project.dto.ProjectResponse;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.project.service.ProjectService;
import com.stolink.backend.domain.project.service.ProjectStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProjectController.class, properties = "spring.jpa.open-in-view=false", excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = com.stolink.backend.global.config.JpaConfig.class))
class ProjectControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private ProjectService projectService;

        @MockBean
        private ProjectStatsService projectStatsService;

        @MockBean
        private ProjectRepository projectRepository;

        @MockBean
        private AnalysisJobRepository analysisJobRepository;

        @MockBean
        private DocumentRepository documentRepository;

        @MockBean
        private AIAnalysisService aiAnalysisService;

        @MockBean
        private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

        @MockBean
        private jakarta.persistence.EntityManagerFactory entityManagerFactory;

        // Security Mocks
        @MockBean
        private com.stolink.backend.global.security.jwt.JwtAuthenticationFilter jwtAuthenticationFilter;

        @MockBean
        private com.stolink.backend.global.security.CsrfOriginFilter csrfOriginFilter;

        @MockBean
        private com.stolink.backend.global.security.oauth2.CustomOAuth2UserService customOAuth2UserService;

        @MockBean
        private com.stolink.backend.global.security.oauth2.OAuth2SuccessHandler oAuth2SuccessHandler;

        @MockBean
        private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

        @Test
        @DisplayName("Update project cover image - Success")
        @WithMockUser
        void updateProjectCoverImage() throws Exception {
                UUID projectId = UUID.randomUUID();
                CreateProjectRequest request = CreateProjectRequest.builder()
                                .coverImage("http://valid-url.com/image.jpg")
                                .build();

                ProjectResponse response = ProjectResponse.builder()
                                .id(projectId)
                                .coverImage("http://valid-url.com/image.jpg")
                                .build();

                when(projectService.updateProject(any(), eq(projectId), any(CreateProjectRequest.class)))
                                .thenReturn(response);

                mockMvc.perform(patch("/api/projects/{id}", projectId)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print())
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Update project with invalid enum - Should fail cleanly")
        @WithMockUser
        void updateProjectInvalidEnum() throws Exception {
                UUID projectId = UUID.randomUUID();
                String json = "{\"genre\": \"INVALID_GENRE\", \"coverImage\": \"http://url.com\"}";

                mockMvc.perform(patch("/api/projects/{id}", projectId)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                                .andDo(print())
                                .andExpect(status().isOk()); // Should be OK because logic handles exception
        }
}
