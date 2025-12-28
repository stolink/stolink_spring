package com.stolink.backend.domain.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateRequest {
    private List<DocumentUpdate> updates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentUpdate {
        private UUID id;
        private UpdateDocumentRequest changes;
    }
}
