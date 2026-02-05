package com.stolink.backend.domain.character.controller;

import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.service.CharacterService;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.user.dto.LoginRequest;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.integration.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CharacterControllerIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private CharacterService characterService;

    @Autowired
    private com.stolink.backend.support.TestDataFactory testDataFactory;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private String getAccessToken(User user) throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(user.getEmail());
        loginRequest.setPassword("password123!");

        return mockMvc.perform(post("/api/auth/login")
                .header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn().getResponse().getCookie("access_token").getValue();
    }

    @Test
    @DisplayName("TC-STO-301: 캐릭터 생성 성공")
    void createCharacter_Success() throws Exception {
        // given
        User user = testDataFactory.createUser("char-creator@example.com", "Creator", "password123!");
        String accessToken = getAccessToken(user);
        Project project = testDataFactory.createProject(user, "Character World");

        Character requestCharacter = Character.builder()
                .name("Alice")
                .role("protagonist")
                .age(20)
                .build();

        Character createdCharacter = Character.builder()
                .id(UUID.randomUUID().toString())
                .projectId(project.getId().toString())
                .name("Alice")
                .role("protagonist")
                .age(20)
                .build();

        given(characterService.createCharacter(eq(user.getId()), eq(project.getId()), any(Character.class)))
                .willReturn(createdCharacter);

        // when
        ResultActions result = mockMvc.perform(post("/api/projects/" + project.getId() + "/characters")
                .header("Origin", "http://localhost:3000")
                .cookie(new Cookie("access_token", accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestCharacter)));

        // then
        result.andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Alice"))
                .andExpect(jsonPath("$.data.role").value("protagonist"));
    }

    @Test
    @DisplayName("TC-STO-302: 프로젝트 캐릭터 목록 조회 성공")
    void getCharacters_Success() throws Exception {
        // given
        User user = testDataFactory.createUser("char-viewer@example.com", "Viewer", "password123!");
        String accessToken = getAccessToken(user);
        Project project = testDataFactory.createProject(user, "View Project");

        Character c1 = Character.builder().id("1").name("Alice").build();
        Character c2 = Character.builder().id("2").name("Bob").build();

        given(characterService.getCharactersWithRelationships(user.getId(), project.getId()))
                .willReturn(List.of(c1, c2));

        // when
        ResultActions result = mockMvc.perform(get("/api/projects/" + project.getId() + "/characters")
                .header("Origin", "http://localhost:3000")
                .cookie(new Cookie("access_token", accessToken)));

        // then
        result.andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("Alice"))
                .andExpect(jsonPath("$.data[1].name").value("Bob"));
    }
}
