package com.stolink.backend.domain.foreshadowing.service;

import com.stolink.backend.domain.foreshadowing.entity.Foreshadowing;
import com.stolink.backend.domain.foreshadowing.repository.ForeshadowingRepository;
import com.stolink.backend.domain.project.entity.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ForeshadowingService {

    private final ForeshadowingRepository foreshadowingRepository;

    /**
     * 프로젝트 복제: Foreshadowing 복제
     * 
     * @param sourceProject 원본 프로젝트
     * @param targetProject 복제 대상 프로젝트
     */
    @Transactional
    public void cloneForeshadowings(Project sourceProject, Project targetProject) {
        List<Foreshadowing> sourceForeshadowings = 
            foreshadowingRepository.findByProject(sourceProject);
        
        for (Foreshadowing source : sourceForeshadowings) {
            Foreshadowing newForeshadowing = Foreshadowing.builder()
                .project(targetProject)
                .tag(source.getTag())
                .description(source.getDescription())
                .importance(source.getImportance())
                .build();
            
            foreshadowingRepository.save(newForeshadowing);
        }
        
        log.info("Cloned {} foreshadowings from project {} to {}", 
            sourceForeshadowings.size(), sourceProject.getId(), targetProject.getId());
    }
}
