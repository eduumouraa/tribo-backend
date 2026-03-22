package com.tribo.modules.auth.service;

import com.tribo.modules.payment.entity.Subscription;
import com.tribo.modules.payment.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Serviço de verificação de assinaturas.
 * Resultado é cacheado no Redis por 15 minutos para evitar
 * consultas repetidas ao banco a cada requisição.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * Verifica se o usuário tem assinatura ativa.
     * Cache key: "subscription:active:{userId}"
     *
     * ADMINS e OWNERs sempre têm acesso — verificado no controller
     * com @PreAuthorize antes de chegar aqui.
     */
    @Cacheable(value = "subscription:active", key = "#userId")
    public boolean hasActiveSubscription(UUID userId) {
        return subscriptionRepository
                .findActiveByUserId(userId)
                .map(Subscription::isActive)
                .orElse(false);
    }

    /**
     * Ativa a assinatura de um usuário após pagamento confirmado.
     * Chamado pelo StripeWebhookService após receber checkout.session.completed.
     */
    public Subscription activate(UUID userId, String plan, String stripeCustomerId,
                                  String stripeSubscriptionId, String providerId) {
        Subscription sub = Subscription.builder()
                .userId(userId)
                .plan(plan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .provider("stripe")
                .providerId(providerId)
                .stripeCustomerId(stripeCustomerId)
                .stripeSubscriptionId(stripeSubscriptionId)
                .build();

        return subscriptionRepository.save(sub);
    }

    /**
     * Migração da Eduzz — cria assinatura manual para alunos existentes.
     * Chamado pelo script de migração via endpoint admin.
     */
    public Subscription activateFromMigration(UUID userId, String plan) {
        Subscription sub = Subscription.builder()
                .userId(userId)
                .plan(plan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .provider("eduzz")   // registra origem para auditoria
                .build();

        return subscriptionRepository.save(sub);
    }
}
