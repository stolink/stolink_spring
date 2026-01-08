package com.stolink.backend.domain.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.ai.entity.ProcessedEvent;

/**
 * ProcessedEvent Repository
 * 
 * RabbitMQ 이벤트의 idempotency 체크용.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    boolean existsByEventId(String eventId);
}
