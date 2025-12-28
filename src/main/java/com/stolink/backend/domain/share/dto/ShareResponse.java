package com.stolink.backend.domain.share.dto;

import com.stolink.backend.domain.share.entity.Share;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ShareResponse {
    private UUID shareId;
    private UUID projectId;
    private boolean hasPassword;

    public static ShareResponse from(Share share) {
        return ShareResponse.builder()
                .shareId(share.getId())
                .projectId(share.getProject().getId())
                .hasPassword(share.getPassword() != null && !share.getPassword().isEmpty())
                .build();
    }
}
