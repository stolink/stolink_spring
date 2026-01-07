package com.stolink.backend.domain.project.service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stolink.backend.domain.character.repository.CharacterRepository;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.project.dto.ChapterStats;
import com.stolink.backend.domain.project.dto.DailyStats;
import com.stolink.backend.domain.project.dto.ProjectStatsResponse;
import com.stolink.backend.domain.project.dto.WritingActivity;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectStatsService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final CharacterRepository characterRepository;

    /**
     * 프로젝트 통계 계산
     */
    public ProjectStatsResponse calculateStats(UUID userId, UUID projectId) {
        // 1. 사용자 및 프로젝트 검증
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        // 2. 문서 조회 및 TEXT 타입 필터링
        List<Document> allDocuments = documentRepository.findByProject(project);
        List<Document> textDocuments = allDocuments.stream()
                .filter(doc -> doc.getType() == Document.DocumentType.TEXT)
                .toList();

        // 3. 기본 통계 계산
        long totalWords = textDocuments.stream()
                .mapToLong(doc -> doc.getWordCount() != null ? doc.getWordCount() : 0L)
                .sum();

        int chapterCount = textDocuments.size();

        // 4. 캐릭터 수 조회 (Neo4j)
        int characterCount = characterRepository.findByProjectId(projectId.toString()).size();

        // 5. 챕터 상세 정보 (순서대로 정렬)
        List<ChapterStats> chapters = textDocuments.stream()
                .sorted(Comparator.comparing(Document::getOrder))
                .map(doc -> ChapterStats.builder()
                        .id(doc.getId())
                        .title(doc.getTitle())
                        .wordCount(doc.getWordCount() != null ? doc.getWordCount().longValue() : 0L)
                        .order(doc.getOrder())
                        .build())
                .collect(Collectors.toList());

        // 6. 집필 활동 집계
        WritingActivity writingActivity = calculateWritingActivity(projectId);

        return ProjectStatsResponse.builder()
                .totalWords(totalWords)
                .chapterCount(chapterCount)
                .characterCount(characterCount)
                .chapters(chapters)
                .writingActivity(writingActivity)
                .build();
    }

    /**
     * 집필 활동 패턴 집계
     */
    private WritingActivity calculateWritingActivity(UUID projectId) {
        // 요일별 집계
        Map<String, Long> byDayOfWeek = aggregateByDayOfWeek(projectId);

        // 시간대별 집계
        Map<String, Long> byTimeOfDay = aggregateByTimeOfDay(projectId);

        // 최근 30일 집계
        List<DailyStats> last30Days = aggregateLast30Days(projectId);

        return WritingActivity.builder()
                .byDayOfWeek(byDayOfWeek)
                .byTimeOfDay(byTimeOfDay)
                .last30Days(last30Days)
                .build();
    }

    /**
     * 요일별 단어 수 집계 (PostgreSQL DOW: 0=일요일, 1=월요일, ..., 6=토요일)
     */
    private Map<String, Long> aggregateByDayOfWeek(UUID projectId) {
        List<Object[]> results = documentRepository.aggregateByDayOfWeek(projectId);

        Map<String, Long> byDayOfWeek = new HashMap<>();
        for (Object[] row : results) {
            int dayNum = ((Number) row[0]).intValue();
            long total = ((Number) row[1]).longValue();

            String dayName = switch (dayNum) {
                case 0 -> "sun";
                case 1 -> "mon";
                case 2 -> "tue";
                case 3 -> "wed";
                case 4 -> "thu";
                case 5 -> "fri";
                case 6 -> "sat";
                default -> "mon"; // 안전 장치
            };

            byDayOfWeek.put(dayName, total);
        }

        // 누락된 요일은 0으로 채우기
        for (String day : Arrays.asList("mon", "tue", "wed", "thu", "fri", "sat", "sun")) {
            byDayOfWeek.putIfAbsent(day, 0L);
        }

        return byDayOfWeek;
    }

    /**
     * 시간대별 단어 수 집계
     */
    private Map<String, Long> aggregateByTimeOfDay(UUID projectId) {
        List<Object[]> results = documentRepository.aggregateByTimeOfDay(projectId);

        Map<String, Long> byTimeOfDay = new HashMap<>();
        for (Object[] row : results) {
            String timeOfDay = (String) row[0];
            long total = ((Number) row[1]).longValue();
            byTimeOfDay.put(timeOfDay, total);
        }

        // 누락된 시간대는 0으로 채우기
        for (String time : Arrays.asList("morning", "afternoon", "evening", "night")) {
            byTimeOfDay.putIfAbsent(time, 0L);
        }

        return byTimeOfDay;
    }

    /**
     * 최근 30일 일별 단어 수 집계
     */
    private List<DailyStats> aggregateLast30Days(UUID projectId) {
        List<Object[]> results = documentRepository.aggregateLast30Days(projectId);

        return results.stream()
                .map(row -> {
                    // PostgreSQL DATE 타입은 java.sql.Date로 반환됨
                    LocalDate date;
                    if (row[0] instanceof Date) {
                        date = ((Date) row[0]).toLocalDate();
                    } else if (row[0] instanceof LocalDate) {
                        date = (LocalDate) row[0];
                    } else {
                        log.warn("Unexpected date type: {}", row[0].getClass());
                        date = LocalDate.now();
                    }

                    long wordCount = ((Number) row[1]).longValue();

                    return DailyStats.builder()
                            .date(date)
                            .wordCount(wordCount)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
