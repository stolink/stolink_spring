package com.stolink.backend.domain.document.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ManuscriptUploadRequest {
    private UUID projectId;
    private UUID parentId;
    private String content;
    private boolean createFolders = true;
}
