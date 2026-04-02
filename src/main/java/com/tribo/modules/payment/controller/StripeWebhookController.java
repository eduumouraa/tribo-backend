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
 * Garantias:
 * 1. Assinatura validada — rejeita qualquer payload sem assinatura Stripe válida.
 * 2. Idempotência — eventos já processados com sucesso são ignorados.
 * 3. Sem @Async — processamento síncrono garante que o Stripe só recebe 200
 *    quando o processamento realmente terminou. Se falhar, retorna 500 e o
 *    Stripe faz retry automático com backoff exponencial.
 * 4. StripeWebhookEvent salvo APÓS o processamento — se o handler lançar
 *    exceção, o evento NÃO é marcado como processado, permitindo retry.
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
        // 1. Valida assinatura — rejeita payload não-Stripe
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Webhook com assinatura inválida: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Assinatura inválida");
        }

        // 2. Idempotência — eventos já processados com sucesso são ignorados
        if (webhookEventRepository.existsById(event.getId())) {
            log.debug("Evento Stripe já processado, ignorando: {}", event.getId());
            return ResponseEntity.ok("already_processed");
        }

        log.info("Evento Stripe recebido: {} — {}", event.getType(), event.getId());

        // 3. Processa de forma síncrona
        // Se lançar exceção → retorna 500 → Stripe faz retry automático
        // StripeWebhookEvent só é salvo APÓS sucesso (passo 4)
        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> {
                    Session session = (Session) event.getDataObjectDeserializer()
                            .getObject().orElse(null);
                    if (session != null) {
                        webhookService.handleCheckoutCompleted(session, event.getId());
                    } else {
                        log.warn("checkout.session.completed sem objeto desserializável — eventId={}", event.getId());
                    }
                }
                case "customer.subscription.deleted" ->
                    webhookService.handleSubscriptionCancelled(event);

                case "customer.subscription.updated" ->
                    webhookService.handleSubscriptionUpdated(event);

                case "invoice.payment_failed" ->
                    webhookService.handlePaymentFailed(event);

                default ->
                    log.debug("Evento Stripe ignorado: {}", event.getType());
            }
        } catch (Exception e) {
            // Loga o erro mas retorna 500 para que o Stripe tente novamente
            log.error("Erro ao processar evento Stripe {} ({}): {}", event.getType(), event.getId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body("processing_error");
        }

        // 4. Marca como processado SOMENTE após sucesso
        webhookEventRepository.save(new StripeWebhookEvent(event.getId(), null));

        return ResponseEntity.ok("ok");
    }
}
