package com.stolink.backend.domain.dialogue.entity;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * AI 분석으로 추출된 대화 엔티티
 */
@Entity
@Table(name = "dialogues")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Dialogue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "dialogue_id", length = 50)
    private String dialogueId; // AI가 생성한 대화 ID (D001 등)

    @Column(columnDefinition = "TEXT")
    private String participants; // JSON array string (legacy)

    @Column(length = 100)
    private String speaker; // 발화자

    @Column(length = 100)
    private String listener; // 청취자

    @Column(columnDefinition = "TEXT")
    private String line; // 대사 내용

    @Column(columnDefinition = "TEXT")
    private String content; // 대화 전체 내용 (legacy)

    @Column(columnDefinition = "TEXT")
    private String significance;

    @Column(columnDefinition = "TEXT")
    private String subtext;

    @Column(length = 50)
    private String emotion; // 발화자 감정

    @Column(name = "chapter_ref")
    private Integer chapterRef;
}
