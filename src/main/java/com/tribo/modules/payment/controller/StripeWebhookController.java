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
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Webhook com assinatura inválida: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Assinatura inválida");
        }

        log.info("Evento Stripe recebido: {} — {}", event.getType(), event.getId());

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

        return ResponseEntity.ok("ok");
    }
}