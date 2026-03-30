package com.tribo.modules.auth.service;

import com.tribo.modules.payment.entity.Subscription;
import com.tribo.modules.payment.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serviço de verificação e gestão de assinaturas.
 *
 * Cache Redis ativo:
 * - hasActiveSubscription() → cache "subscription-check" por 10 min
 *   Invalidado automaticamente em activate(), cancel(), activateManual()
 *
 * Com 10k usuários simultâneos e 10+ requests/min cada, sem cache teríamos
 * 100k+ queries/min só para checar assinatura. Com cache → ~0 queries adicionais.
 *
 * Controle de acesso por plano:
 * - "tribo"   → acessa cursos required_plan = "tribo"
 * - "financas" → acessa cursos required_plan = "financas"
 * - "combo"   → acessa todos os cursos
 * - ADMIN/OWNER → bypass verificado no controller
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    // ── Verificação de acesso ─────────────────────────────────────

    /**
     * Verifica se o usuário tem alguma assinatura ativa.
     * Cache de 10 minutos — evita hit no banco em todo request autenticado.
     */
    @Cacheable(value = "subscription-check", key = "#userId.toString()")
    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(UUID userId) {
        return subscriptionRepository
                .findActiveByUserId(userId)
                .map(Subscription::isActive)
                .orElse(false);
    }

    /**
     * Verifica se o plano ativo do usuário cobre o required_plan do curso.
     *
     * Regras:
     * - "combo"   cobre tudo
     * - "tribo"   cobre "tribo" e "free"
     * - "financas" cobre "financas" e "free"
     * - "free"    cobre apenas "free"
     */
    @Transactional(readOnly = true)
    public boolean hasAccessToCourse(UUID userId, String requiredPlan) {
        if (requiredPlan == null || "free".equals(requiredPlan)) {
            return true;
        }

        return subscriptionRepository
                .findActiveByUserId(userId)
                .map(sub -> sub.isActive() && planCovers(sub.getPlan(), requiredPlan))
                .orElse(false);
    }

    /**
     * Retorna o plano ativo do usuário, ou null se não tiver assinatura.
     */
    @Transactional(readOnly = true)
    public String getActivePlan(UUID userId) {
        return subscriptionRepository
                .findActiveByUserId(userId)
                .filter(Subscription::isActive)
                .map(Subscription::getPlan)
                .orElse(null);
    }

    // ── Ativação e cancelamento ───────────────────────────────────

    /**
     * Ativa assinatura após pagamento confirmado via Stripe webhook.
     * Invalida o cache para que o próximo request reflita o novo estado.
     */
    @CacheEvict(value = "subscription-check", key = "#userId.toString()")
    @Transactional
    public Subscription activate(UUID userId, String plan, String stripeCustomerId,
                                  String stripeSubscriptionId, String providerId) {
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
     * Libera acesso manualmente — admin sem pagamento, ou migração Eduzz.
     */
    @CacheEvict(value = "subscription-check", key = "#userId.toString()")
    @Transactional
    public Subscription activateManual(UUID userId, String plan, String provider) {
        subscriptionRepository.findActiveByUserId(userId).ifPresent(existing -> {
            existing.setStatus(Subscription.SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(existing);
        });

        Subscription sub = Subscription.builder()
                .userId(userId)
                .plan(plan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .provider(provider)
                .build();

        log.info("Acesso liberado manualmente para userId={}, provider={}", userId, provider);
        return subscriptionRepository.save(sub);
    }

    /**
     * Cancela assinatura — chamado pelo webhook Stripe ou pelo cancelamento na plataforma.
     */
    @CacheEvict(value = "subscription-check", key = "#userId.toString()")
    @Transactional
    public void cancel(UUID userId) {
        subscriptionRepository.findActiveByUserId(userId).ifPresent(sub -> {
            sub.setStatus(Subscription.SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(sub);
            log.info("Assinatura cancelada para userId={}", userId);
        });
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Regras de cobertura de plano:
     * combo   → cobre tudo
     * tribo   → cobre "tribo" e "free"
     * financas → cobre "financas" e "free"
     */
    private boolean planCovers(String userPlan, String requiredPlan) {
        if ("combo".equals(userPlan)) return true;
        if ("free".equals(requiredPlan)) return true;
        return userPlan.equals(requiredPlan);
    }
}
