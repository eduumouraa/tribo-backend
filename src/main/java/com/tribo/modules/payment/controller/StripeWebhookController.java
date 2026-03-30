package com.tribo.modules.payment.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.tribo.modules.payment.entity.StripeWebhookEvent;
import com.tribo.modules.payment.repository.StripeWebhookEventRepository;
import com.tribo.modules.payment.service.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recebe e valida eventos do Stripe.
 *
 * Garante idempotência: eventos já processados são ignorados silenciosamente.
 * O Stripe pode reenviar o mesmo evento em caso de timeout ou falha de rede.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final StripeWebhookService webhookService;
    private final StripeWebhookEventRepository webhookEventRepository;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Webhook com assinatura inválida: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Assinatura inválida");
        }

        // Idempotência: ignora eventos já processados
        if (webhookEventRepository.existsById(event.getId())) {
            log.debug("Evento Stripe já processado, ignorando: {}", event.getId());
            return ResponseEntity.ok("already_processed");
        }

        log.info("Evento Stripe recebido: {} — {}", event.getType(), event.getId());

        // Registra o evento ANTES de processar (garante que não processa duas vezes em concorrência)
        webhookEventRepository.save(new StripeWebhookEvent(event.getId(), null));

        String eventType = event.getType();
        switch (eventType) {
            case "checkout.session.completed" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject().orElse(null);
                if (session != null) {
                    webhookService.handleCheckoutCompleted(session, event.getId());
                }
            }
            case "customer.subscription.deleted" ->
                webhookService.handleSubscriptionCancelled(event);

            case "invoice.payment_failed" ->
                webhookService.handlePaymentFailed(event);

            default ->
                log.debug("Evento Stripe ignorado: {}", event.getType());
        }

        return ResponseEntity.ok("ok");
    }
}
