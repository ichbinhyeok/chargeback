package com.example.demo.dispute.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeoEventRepository extends JpaRepository<SeoEventEntity, UUID> {
    List<SeoEventEntity> findByOccurredAtBetween(Instant fromInclusive, Instant toExclusive);
}
