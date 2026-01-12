package com.stolink.backend.domain.character.mapper;

import org.springframework.stereotype.Component;

import com.stolink.backend.domain.character.dto.CharacterResponse;
import com.stolink.backend.domain.character.node.Character;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterMapper {

    public CharacterResponse toResponse(Character character) {
        if (character == null)
            return null;

        // Delegate to CharacterResponse.from() to ensure relations.graph enrichment
        return CharacterResponse.from(character);
    }
}
