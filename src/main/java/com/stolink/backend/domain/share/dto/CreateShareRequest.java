package com.stolink.backend.domain.share.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateShareRequest {
    private String password;
    private String operations; // Unused
    private String expiresIn; // "7d", "30d", or null
}
