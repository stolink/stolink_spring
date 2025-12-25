package com.stolink.backend.domain.foreshadowing.repository;

import com.stolink.backend.domain.foreshadowing.entity.ForeshadowingAppearance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ForeshadowingAppearanceRepository extends JpaRepository<ForeshadowingAppearance, UUID> {
}
