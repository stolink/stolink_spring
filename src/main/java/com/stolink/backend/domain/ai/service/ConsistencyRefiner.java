package com.stolink.backend.domain.ai.service;

import com.stolink.backend.domain.ai.dto.callback.ConsistencyReportDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 분석 결과(일관성 보고서)를 후처리하여 품질을 개선하는 서비스
 * - 중복 제거 (대칭적 관계)
 * - 중요도 상향 조정 (치명적 논리 오류)
 * - 불필요한 정보 필터링
 * - 제안(Suggestion) 생성
 */
@Slf4j
@Service
public class ConsistencyRefiner {

    private static final int MAX_CONFLICTS = 20;

    // HIGH severity keywords
    private static final List<String> HIGH_SEVERITY_KEYWORDS = List.of(
            "죽은", "사망", "부활", "dead", "alive", "resurrect", // Life/Death
            "시간", "time", "date", // Time
            "잃어", "loss", "gain", "acquire", "item" // Items
    );

    // Context keywords for generating suggestions
    private static final Map<String, String> SUGGESTION_TEMPLATES = Map.of(
            "죽은", "해당 캐릭터의 생존 여부를 타임라인에서 확인하고, 사망 시점 이후의 등장을 삭제하거나 회상신으로 처리하세요.",
            "성격", "캐릭터 시트의 성격 키워드와 해당 장면의 행동이 일치하는지 재검토하세요.",
            "관계", "두 캐릭터 간의 관계 변화 계기가 명확한지 확인하고, 필요하다면 연결 장면을 추가하세요.");

    public List<ConsistencyReportDTO.ConflictDTO> refineConflicts(List<ConsistencyReportDTO.ConflictDTO> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) {
            return new ArrayList<>();
        }

        log.debug("Refining consistency report. Input size: {}", conflicts.size());

        // 1. Deduplication (Merge symmetric relationship conflicts)
        List<ConsistencyReportDTO.ConflictDTO> deduplicated = deduplicate(conflicts);

        // 2. Adjust Severity & Enhance Descriptions
        List<ConsistencyReportDTO.ConflictDTO> enhanced = deduplicated.stream()
                .map(this::enhanceConflict)
                .collect(Collectors.toList());

        // 3. Filter Noise (Remove trivial description gaps if needed, implementation
        // simplified here to filter nulls)
        List<ConsistencyReportDTO.ConflictDTO> filtered = enhanced.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 4. Sort (High -> Med -> Low)
        filtered.sort((c1, c2) -> {
            int score1 = getSeverityScore(c1.getSeverity());
            int score2 = getSeverityScore(c2.getSeverity());
            return Integer.compare(score2, score1); // Descending
        });

        // 5. Slice
        if (filtered.size() > MAX_CONFLICTS) {
            filtered = filtered.subList(0, MAX_CONFLICTS);
        }

        log.debug("Refinement complete. Output size: {}", filtered.size());
        return filtered;
    }

    private List<ConsistencyReportDTO.ConflictDTO> deduplicate(List<ConsistencyReportDTO.ConflictDTO> conflicts) {
        List<ConsistencyReportDTO.ConflictDTO> result = new ArrayList<>();
        Set<String> processedSignatures = new HashSet<>();

        for (ConsistencyReportDTO.ConflictDTO conflict : conflicts) {
            if (conflict.getDescription() == null)
                continue;

            // Simple signature based on type and meaningful keywords or simple dedupe
            // For relationship symmetry: "A vs B" should match "B vs A"
            // We'll use a simplified heuristic: if type is RELATIONSHIP and description
            // contains names.
            // Since we don't have structured names, we rely on full description uniqueness
            // for most,
            // but can implement detailed parsing if names were separate fields.
            // For now, we will deduplicate exact Description matches and very close
            // matches.

            // Refined Logic: If description is "A and B...", signature could be sorted
            // tokens
            // But preserving narrative is safer. We will stick to eliminating duplicates
            // where description is very similar (Levenshtein would be heavy, so using
            // normalized string).

            String signature = conflict.getDescription().trim().toLowerCase();

            // Attempt to detect "A vs B" symmetry if "vs" or "and" is present
            // This is hard without NER. We will rely on the AI's tendency to output similar
            // strings.

            if (processedSignatures.contains(signature)) {
                continue;
            }
            processedSignatures.add(signature);
            result.add(conflict);
        }
        return result;
    }

    private ConsistencyReportDTO.ConflictDTO enhanceConflict(ConsistencyReportDTO.ConflictDTO original) {
        ConsistencyReportDTO.ConflictDTO enhanced = ConsistencyReportDTO.ConflictDTO.builder()
                .type(original.getType())
                .description(original.getDescription())
                .severity(original.getSeverity())
                .resolution(original.getResolution())
                .suggestion(original.getSuggestion())
                .location(original.getLocation())
                .build();

        String originalDesc = original.getDescription();
        if (originalDesc == null)
            return enhanced;

        // Severity Promotion
        boolean isHighSeverity = HIGH_SEVERITY_KEYWORDS.stream().anyMatch(originalDesc::contains);
        if (isHighSeverity) {
            enhanced.setSeverity("HIGH");
        }

        // Generate Suggestion if missing
        if (enhanced.getSuggestion() == null || enhanced.getSuggestion().isEmpty()) {
            // Use resolution if available
            if (enhanced.getResolution() != null && !enhanced.getResolution().isEmpty()) {
                enhanced.setSuggestion(enhanced.getResolution());
            } else {
                // Heuristic generation
                enhanced.setSuggestion(generateSuggestion(originalDesc));
            }
        }

        return enhanced;
    }

    private String generateSuggestion(String description) {
        for (Map.Entry<String, String> entry : SUGGESTION_TEMPLATES.entrySet()) {
            if (description.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "해당 모순을 해결하기 위해 캐릭터의 설정이나 전개 흐름을 수정하는 것을 고려해보세요.";
    }

    private int getSeverityScore(String severity) {
        if (severity == null)
            return 0;
        switch (severity.toUpperCase()) {
            case "HIGH":
                return 3;
            case "MEDIUM":
                return 2;
            case "LOW":
                return 1;
            default:
                return 0;
        }
    }
}
