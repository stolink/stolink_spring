package com.stolink.backend.domain.character.controller;

import com.stolink.backend.domain.character.node.Character;
import com.stolink.backend.domain.character.service.CharacterService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @GetMapping("/projects/{pid}/characters")
    public ApiResponse<List<Character>> getCharacters(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID pid) {
        List<Character> characters = characterService.getCharacters(userId, pid);
        return ApiResponse.ok(characters);
    }

    @GetMapping("/projects/{pid}/relationships")
    public ApiResponse<List<Character>> getRelationships(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID pid) {
        List<Character> characters = characterService.getCharactersWithRelationships(userId, pid);
        return ApiResponse.ok(characters);
    }

    @PostMapping("/projects/{pid}/characters")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Character> createCharacter(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID pid,
            @RequestBody Character character) {
        Character created = characterService.createCharacter(userId, pid, character);
        return ApiResponse.created(created);
    }

    @PostMapping("/relationships")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> createRelationship(
            @RequestHeader("X-User-Id") UUID userId,
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

    @DeleteMapping("/characters/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCharacter(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable String id) {
        characterService.deleteCharacter(userId, id);
    }
}
