package com.tribo.modules.auth.service;

import com.tribo.modules.payment.entity.Subscription;
import com.tribo.modules.payment.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serviço de verificação e gestão de assinaturas.
 *
 * NOTA: @Cacheable removido intencionalmente — entidades JPA com
 * coleções LAZY não serializam bem no Redis. Cache será reativado
 * quando implementarmos DTOs serializáveis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * Verifica se o usuário tem assinatura ativa.
     * ADMINS e OWNERs têm acesso garantido — verificado no AuthService.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(UUID userId) {
        return subscriptionRepository
                .findActiveByUserId(userId)
                .map(Subscription::isActive)
                .orElse(false);
    }

    /**
     * Ativa a assinatura após pagamento confirmado via Stripe webhook.
     */
    @Transactional
    public Subscription activate(UUID userId, String plan, String stripeCustomerId,
                                  String stripeSubscriptionId, String providerId) {
        // Cancela assinatura anterior se existir
        subscriptionRepository.findActiveByUserId(userId).ifPresent(existing -> {
            existing.setStatus(Subscription.SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(existing);
            log.info("Assinatura anterior cancelada para userId={}", userId);
        });

        Subscription sub = Subscription.builder()
                .userId(userId)
                .plan(plan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .provider("stripe")
                .providerId(providerId)
                .stripeCustomerId(stripeCustomerId)
                .stripeSubscriptionId(stripeSubscriptionId)
                .build();

        log.info("Assinatura ativada para userId={}, plano={}", userId, plan);
        return subscriptionRepository.save(sub);
    }

    /**
     * Libera acesso manualmente — para admin dar acesso sem pagamento,
     * ou para migração de alunos de outra plataforma (Eduzz, Hotmart).
     */
    @Transactional
    public Subscription activateManual(UUID userId, String plan, String provider) {
        // Cancela assinatura anterior se existir
        subscriptionRepository.findActiveByUserId(userId).ifPresent(existing -> {
            existing.setStatus(Subscription.SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(existing);
        });

        Subscription sub = Subscription.builder()
                .userId(userId)
                .plan(plan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .provider(provider) // "manual", "eduzz", "hotmart", etc
                .build();

        log.info("Acesso liberado manualmente para userId={}, provider={}", userId, provider);
        return subscriptionRepository.save(sub);
    }

    /**
     * Cancela a assinatura — chamado pelo webhook do Stripe
     * quando o pagamento falha ou o aluno cancela.
     */
    @Transactional
    public void cancel(UUID userId) {
        subscriptionRepository.findActiveByUserId(userId).ifPresent(sub -> {
            sub.setStatus(Subscription.SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(sub);
            log.info("Assinatura cancelada para userId={}", userId);
        });
    }
}
