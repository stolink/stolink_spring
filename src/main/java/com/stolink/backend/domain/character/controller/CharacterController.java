package com.stolink.backend.domain.character.controller;

import com.stolink.backend.domain.character.dto.CharacterResponse;
import com.stolink.backend.domain.character.dto.ImageGenerationRequest;
import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.service.CharacterService;
import com.stolink.backend.global.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @GetMapping("/projects/{projectId}/characters")
    public ApiResponse<List<CharacterResponse>> getCharacters(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {
        List<Character> characters = characterService.getCharactersWithRelationships(userId, projectId);
        return ApiResponse.ok(characters.stream()
                .map(CharacterResponse::from)
                .collect(Collectors.toList()));
    }

    @GetMapping("/characters")
    public ApiResponse<List<CharacterResponse>> getAllCharacters() {
        List<Character> characters = characterService.getAllCharacters();
        return ApiResponse.ok(characters.stream()
                .map(CharacterResponse::from)
                .collect(Collectors.toList()));
    }

    @GetMapping("/characters/{characterId}")
    public ApiResponse<CharacterResponse> getCharacter(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String characterId) {
        Character character = characterService.getCharacterById(userId, characterId);
        return ApiResponse.ok(CharacterResponse.from(character));
    }

    @PostMapping("/projects/{projectId}/characters")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CharacterResponse> createCharacter(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId,
            @RequestBody Character character) {
        Character created = characterService.createCharacter(userId, projectId, character);
        return ApiResponse.created(CharacterResponse.from(created));
    }

    @PostMapping("/relationships")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> createRelationship(
            @AuthenticationPrincipal UUID userId,
            @RequestBody Map<String, Object> body) {
        characterService.createRelationship(
                userId,
                (String) body.get("sourceId"),
                (String) body.get("targetId"),
                (String) body.get("type"),
                (Integer) body.get("strength"),
                (String) body.get("description"));
        return ApiResponse.created(null);
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
}
