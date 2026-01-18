package com.stolink.backend.domain.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 프로젝트 복제 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCloneRequest {
    
    @NotBlank(message = "프로젝트 제목은 필수입니다.")
    private String newTitle;
}
