package com.stolink.backend.domain.event.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.ai.dto.callback.EventDTO;
import com.stolink.backend.domain.event.entity.EventEntity;
import com.stolink.backend.domain.event.repository.EventJpaRepository;
import com.stolink.backend.domain.project.entity.Project;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이벤트 중복 탐지 서비스
 *
 * Strategy:
 * 1. Chapter Match (Required)
 * 2. Hybrid Similarity (OR condition):
 *    - Participants Jaccard Similarity > 0.8
 *    - Embedding Cosine Similarity > 0.85
 *    - Narrative Summary Jaccard Similarity > 0.5 (Fallback)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventDeduplicationService {

    private final EventJpaRepository eventJpaRepository;
    private final ObjectMapper objectMapper;

    // Thresholds
    private static final double PARTICIPANTS_THRESHOLD = 0.8;
    private static final double EMBEDDING_THRESHOLD = 0.85;
    private static final double SUMMARY_THRESHOLD = 0.5;

    @Transactional(readOnly = true)
    public Optional<EventEntity> findDuplicateEvent(Project project, EventDTO newEvent) {
        if (newEvent.getChapter() == null) {
            log.warn("Cannot deduplicate event without chapter: {}", newEvent.getEventId());
            return Optional.empty();
        }

        List<EventEntity> candidates = eventJpaRepository.findAllByProjectAndChapter(project, newEvent.getChapter());

        return findDuplicateEventInCandidates(candidates, newEvent);
    }

    /**
     * Check for duplicates within a provided list of candidates (Batch optimized)
     */
    public Optional<EventEntity> findDuplicateEventInCandidates(List<EventEntity> candidates, EventDTO newEvent) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(existing -> isSameEvent(existing, newEvent))
                .findFirst();
    }

    private boolean isSameEvent(EventEntity existing, EventDTO newEvent) {
        // 1. Check ID overlap (Strong signal)
        if (existing.getEventId() != null && existing.getEventId().equals(newEvent.getEventId())) {
            return true;
        }

        // 2. Check Participants Similarity
        boolean participantsMatch = false;
        try {
            List<String> existingParticipants = parseParticipants(existing.getParticipants());
            double score = calculateJaccardSimilarity(existingParticipants, newEvent.getParticipants());
            if (score >= PARTICIPANTS_THRESHOLD) {
                participantsMatch = true;
            }
        } catch (Exception e) {
            log.warn("Failed to compare participants: {}", e.getMessage());
        }

        // 3. Check Embedding Similarity
        boolean embeddingMatch = false;
        try {
            List<Double> existingEmbedding = parseEmbedding(existing.getEmbeddingJson());
            if (existingEmbedding != null && newEvent.getEmbedding() != null) {
                double score = calculateCosineSimilarity(existingEmbedding, newEvent.getEmbedding());
                if (score >= EMBEDDING_THRESHOLD) {
                    embeddingMatch = true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to compare embeddings: {}", e.getMessage());
        }

        // 4. Fallback: Narrative Summary Similarity (if others are weak)
        boolean summaryMatch = false;
        if (!participantsMatch && !embeddingMatch) {
            double score = calculateStringJaccardSimilarity(existing.getNarrativeSummary(), newEvent.getNarrativeSummary());
            if (score >= SUMMARY_THRESHOLD) {
                summaryMatch = true;
            }
        }

        // Decision Logic
        return participantsMatch || embeddingMatch || summaryMatch;
    }

    // --- Utility Methods ---

    private List<String> parseParticipants(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<Double> parseEmbedding(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private double calculateJaccardSimilarity(List<String> list1, List<String> list2) {
        if (list1 == null || list2 == null || (list1.isEmpty() && list2.isEmpty())) return 0.0;

        Set<String> set1 = new HashSet<>(list1);
        Set<String> set2 = new HashSet<>(list2);

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    private double calculateCosineSimilarity(List<Double> vec1, List<Double> vec2) {
        if (vec1 == null || vec2 == null || vec1.size() != vec2.size()) return 0.0;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            normA += Math.pow(vec1.get(i), 2);
            normB += Math.pow(vec2.get(i), 2);
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double calculateStringJaccardSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;

        // Simple character-level 2-gram (bigram) Jaccard
        Set<String> grams1 = getBigrams(s1);
        Set<String> grams2 = getBigrams(s2);

        Set<String> intersection = new HashSet<>(grams1);
        intersection.retainAll(grams2);

        Set<String> union = new HashSet<>(grams1);
        union.addAll(grams2);

        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    private Set<String> getBigrams(String text) {
        Set<String> grams = new HashSet<>();
        if (text == null || text.length() < 2) return grams;

        String clean = text.replaceAll("\\s+", "").toLowerCase(); // Remove spaces, lowercase
        for (int i = 0; i < clean.length() - 1; i++) {
            grams.add(clean.substring(i, i + 2));
        }
        return grams;
    }
}
