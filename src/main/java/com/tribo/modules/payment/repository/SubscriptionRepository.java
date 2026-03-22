package com.tribo.modules.payment.repository;

import com.tribo.modules.payment.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Query("SELECT s FROM Subscription s WHERE s.userId = :userId AND s.status = 'ACTIVE' AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP) ORDER BY s.createdAt DESC LIMIT 1")
    Optional<Subscription> findActiveByUserId(UUID userId);

    boolean existsByStripeSubscriptionId(String stripeSubscriptionId);
}
