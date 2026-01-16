package com.stolink.backend.domain.ai.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.ai.entity.CharacterTimeline;

/**
 * CharacterTimeline Repository (읽기 전용)
 *
 * FastAPI AI Backend에서 데이터를 쓰며, Spring에서는 읽기 전용으로 사용합니다.
 */
@Repository
public interface CharacterTimelineRepository extends JpaRepository<CharacterTimeline, UUID> {

        /**
         * 특정 프로젝트의 특정 캐릭터 타임라인 전체 조회
         */
        List<CharacterTimeline> findByProjectIdAndCharacterName(UUID projectId, String characterName);

        /**
         * 특정 프로젝트의 특정 챕터에 등장하는 모든 캐릭터 타임라인 조회
         */
        List<CharacterTimeline> findByProjectIdAndChapter(UUID projectId, Integer chapter);

        /**
         * 특정 프로젝트의 특정 캐릭터의 특정 챕터 타임라인 조회
         */
        Optional<CharacterTimeline> findByProjectIdAndCharacterNameAndChapter(
                        UUID projectId, String characterName, Integer chapter);

        /**
         * 특정 프로젝트의 모든 캐릭터 타임라인 조회
         */
        List<CharacterTimeline> findByProjectId(UUID projectId);

        /**
         * 특정 문서의 모든 캐릭터 타임라인 조회
         */
        List<CharacterTimeline> findByDocumentId(UUID documentId);

        /**
         * 특정 캐릭터의 타임라인을 챕터 순으로 정렬하여 조회
         */
        List<CharacterTimeline> findByProjectIdAndCharacterNameOrderByChapterAsc(
                        UUID projectId, String characterName);

        /**
         * 특정 프로젝트의 타임라인 전체 삭제
         */
        void deleteByProjectId(UUID projectId);
}
