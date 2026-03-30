package com.tribo.modules.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Registro de eventos Stripe já processados — garante idempotência.
 * O Stripe pode reenviar o mesmo evento em caso de falha de rede.
 * Antes de processar, verificamos se o eventId já está aqui.
 */
@Entity
@Table(name = "stripe_webhook_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StripeWebhookEvent {

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt = OffsetDateTime.now();
}
