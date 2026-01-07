package com.stolink.backend.global.infrastructure.storead.dto;

import lombok.Builder;

@Builder
public record StoreadPublishRequest(
    String authorEmail,
    String workTitle,
    String workSynopsis,
    String workGenre,
    String workCoverUrl,
    String chapterTitle,
    String chapterContent
) {}
