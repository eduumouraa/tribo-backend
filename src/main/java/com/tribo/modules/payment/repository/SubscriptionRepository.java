package com.tribo.modules.payment.repository;

import com.tribo.modules.payment.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Query("SELECT s FROM Subscription s WHERE s.userId = :userId AND s.status = 'ACTIVE' AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP) ORDER BY s.createdAt DESC LIMIT 1")
    Optional<Subscription> findActiveByUserId(UUID userId);

    boolean existsByStripeSubscriptionId(String stripeSubscriptionId);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    /**
     * Assinaturas ACTIVE com expiresAt no passado — para o job de expiração.
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.expiresAt IS NOT NULL AND s.expiresAt <= :agora")
    List<Subscription> findExpiredSubscriptions(@Param("agora") OffsetDateTime agora);

    /**
     * Assinaturas ACTIVE que vencem entre agora e uma data futura — para notificação prévia.
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.expiresAt IS NOT NULL AND s.expiresAt BETWEEN :inicio AND :fim")
    List<Subscription> findExpiringBetween(@Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);
}
