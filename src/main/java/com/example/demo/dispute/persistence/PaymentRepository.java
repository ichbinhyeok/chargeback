package com.example.demo.dispute.persistence;

import com.example.demo.dispute.domain.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByCheckoutSessionId(String checkoutSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.checkoutSessionId = :checkoutSessionId")
    Optional<PaymentEntity> findByCheckoutSessionIdForUpdate(@Param("checkoutSessionId") String checkoutSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from PaymentEntity p
            where p.id = :paymentId and p.provider = :provider
            """)
    Optional<PaymentEntity> findByIdAndProviderForUpdate(
            @Param("paymentId") UUID paymentId,
            @Param("provider") String provider
    );

    Optional<PaymentEntity> findFirstByDisputeCaseIdOrderByCreatedAtDesc(UUID caseId);

    Optional<PaymentEntity> findFirstByDisputeCaseIdAndProviderOrderByCreatedAtDesc(UUID caseId, String provider);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from PaymentEntity p
            where p.disputeCase.id = :caseId and p.provider = :provider
            order by p.createdAt desc
            """)
    List<PaymentEntity> findLockedByDisputeCaseIdAndProviderOrderByCreatedAtDesc(
            @Param("caseId") UUID caseId,
            @Param("provider") String provider
    );

    Optional<PaymentEntity> findFirstByDisputeCaseIdAndStatusOrderByCreatedAtDesc(UUID caseId, PaymentStatus status);

    boolean existsByDisputeCaseIdAndStatus(UUID caseId, PaymentStatus status);

    List<PaymentEntity> findByDisputeCaseId(UUID caseId);
}
