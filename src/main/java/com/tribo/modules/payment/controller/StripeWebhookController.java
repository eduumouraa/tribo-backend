package com.tribo.modules.payment.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.tribo.modules.payment.service.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recebe eventos do Stripe via webhook.
 *
 * IMPORTANTE: esta rota é pública mas verifica a assinatura
 * criptográfica do Stripe — só o Stripe consegue enviar eventos válidos.
 *
 * Configurar no Dashboard do Stripe:
 * Developers → Webhooks → Add endpoint
 * URL: https://api.seudominio.com/api/v1/webhooks/stripe
 *
 * Eventos a habilitar:
 * - checkout.session.completed     → pagamento aprovado (ativa assinatura)
 * - customer.subscription.deleted  → assinatura cancelada
 * - invoice.payment_failed         → pagamento falhou
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final StripeWebhookService webhookService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        Event event;

        try {
            // Verifica que o evento veio mesmo do Stripe (assinatura HMAC)
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Webhook com assinatura inválida: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Assinatura inválida");
        }

        log.info("Evento Stripe recebido: {} — {}", event.getType(), event.getId());

        // Delega para o service — processamento assíncrono para responder
        // o Stripe rapidamente (ele espera 200 em até 30s)
        String eventType = event.getType();
        if ("checkout.session.completed".equals(eventType)) {
            Session session = (Session) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
            if (session != null) {
                webhookService.handleCheckoutCompleted(session, event.getId());
            }
        } else if ("customer.subscription.deleted".equals(eventType)) {
            webhookService.handleSubscriptionCancelled(event);
        } else if ("invoice.payment_failed".equals(eventType)) {
            webhookService.handlePaymentFailed(event);
        } else {
            log.debug("Evento ignorado: {}", event.getType());
        }

        // Sempre retorna 200 para o Stripe não retentar
        return ResponseEntity.ok("ok");
    }
}
