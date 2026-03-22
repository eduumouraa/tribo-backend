package com.tribo.modules.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Assinatura de um usuário.
 *
 * Um usuário pode ter múltiplas assinaturas (histórico),
 * mas apenas uma ACTIVE por vez.
 *
 * Plans:
 * - tribo     → acesso ao Curso Tribo do Investidor
 * - financas  → acesso ao Curso de Organização Financeira
 * - combo     → acesso a ambos os cursos
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Plano contratado: tribo | financas | combo */
    @Column(nullable = false, length = 50)
    private String plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    /** Origem do pagamento: stripe | manual | eduzz (migração) */
    @Column(nullable = false, length = 50)
    private String provider;

    /** ID do Payment Intent ou Session no Stripe */
    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    /** null = sem expiração (assinatura vitalícia ou recorrente ativa) */
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public enum SubscriptionStatus {
        TRIAL,      // período de teste
        ACTIVE,     // pagamento confirmado
        CANCELLED,  // cancelado pelo usuário
        EXPIRED     // expirou sem renovação
    }

    /** Verifica se a assinatura está ativa e não expirou */
    public boolean isActive() {
        if (this.status != SubscriptionStatus.ACTIVE) return false;
        if (this.expiresAt == null) return true;            // vitalícia
        return this.expiresAt.isAfter(OffsetDateTime.now());
    }
}
