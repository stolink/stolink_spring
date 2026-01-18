package com.stolink.backend.domain.character.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stolink.backend.domain.character.dto.CharacterResponse;
import com.stolink.backend.domain.character.dto.ImageGenerationRequest;
import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.service.CharacterService;
import com.stolink.backend.global.common.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;
    private final com.stolink.backend.domain.character.mapper.CharacterMapper characterMapper;

    @GetMapping("/projects/{projectId}/characters")
    public ApiResponse<List<CharacterResponse>> getCharacters(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {
        List<Character> characters = characterService.getCharactersWithRelationships(userId, projectId);
        return ApiResponse.ok(characters.stream()
                .map(characterMapper::toResponse)
                .collect(Collectors.toList()));
    }

    @GetMapping("/characters")
    public ApiResponse<List<CharacterResponse>> getAllCharacters() {
        List<Character> characters = characterService.getAllCharacters();
        return ApiResponse.ok(characters.stream()
                .map(characterMapper::toResponse)
                .collect(Collectors.toList()));
    }

    @GetMapping("/characters/{characterId}")
    public ApiResponse<CharacterResponse> getCharacter(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String characterId) {
        Character character = characterService.getCharacterById(userId, characterId);
        return ApiResponse.ok(characterMapper.toResponse(character));
    }

    @PostMapping("/projects/{projectId}/characters")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CharacterResponse> createCharacter(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId,
            @RequestBody Character character) {
        Character created = characterService.createCharacter(userId, projectId, character);
        return ApiResponse.created(characterMapper.toResponse(created));
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Object obj) {
        if (obj instanceof List<?>) {
            return (List<String>) obj;
        }
        return List.of();
    }

    @PostMapping("/characters/seed")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> seedCharacters() {
        characterService.seedDummyData();
        return ApiResponse.created(null);
    }

    @DeleteMapping("/characters/{characterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCharacter(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String characterId) {
        characterService.deleteCharacter(userId, characterId);
    }

    /**
     * 캐릭터 이미지 생성 요청
     * RabbitMQ를 통해 FastAPI 이미지 워커로 전송
     */
    @PostMapping("/projects/{projectId}/characters/{characterId}/image")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Map<String, String>> triggerImageGeneration(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId,
            @PathVariable UUID characterId,
            @Valid @RequestBody ImageGenerationRequest request) {

        String jobId = characterService.triggerImageGeneration(
                userId, projectId, characterId, request.description(), request.action(), request.setting());

        return ApiResponse.accepted(Map.of("jobId", jobId));
    }

    /**
     * 캐릭터 정보 업데이트 (이름, 외형, 위치 등)
     */
    @PatchMapping("/characters/{characterId}")
    public ApiResponse<CharacterResponse> updateCharacter(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String characterId,
            @RequestBody com.stolink.backend.domain.character.dto.CharacterUpdateRequest request) {
        Character updated = characterService.updateCharacter(
                userId, characterId, request);
        return ApiResponse.ok(characterMapper.toResponse(updated));
    }
}
