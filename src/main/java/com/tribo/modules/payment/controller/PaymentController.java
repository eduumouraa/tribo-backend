package com.tribo.modules.payment.controller;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.tribo.modules.auth.service.SubscriptionService;
import com.tribo.modules.payment.repository.SubscriptionRepository;
import com.tribo.modules.user.entity.User;
import com.tribo.shared.exception.BusinessException;
import com.tribo.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * Endpoints de pagamento via Stripe.
 *
 * POST /api/v1/payments/checkout       — cria sessão de checkout
 * POST /api/v1/subscriptions/cancel    — cancela assinatura recorrente
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pagamentos", description = "Checkout Stripe e gestão de assinaturas")
public class PaymentController {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @Value("${stripe.price.tribo:}")
    private String stripePriceTribo;

    @Value("${stripe.price.financas:}")
    private String stripePriceFinancas;

    @Value("${stripe.price.combo:}")
    private String stripePriceCombo;

    @Value("${app.frontend-url:http://localhost:8080}")
    private String frontendUrl;

    private static final Set<String> VALID_PLANS = Set.of("tribo", "financas", "combo");

    // ── Checkout ─────────────────────────────────────────────────

    @Operation(summary = "Criar sessão de checkout Stripe para o plano escolhido")
    @PostMapping("/api/v1/payments/checkout")
    public ResponseEntity<CheckoutResponse> createCheckout(
            @Valid @RequestBody CheckoutRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        if (!VALID_PLANS.contains(request.plan())) {
            throw new BusinessException("Plano inválido. Escolha: tribo, financas ou combo.");
        }

        String priceId = resolvePriceId(request.plan());
        if (priceId == null || priceId.isBlank()) {
            throw new BusinessException("Este plano ainda não está disponível para compra. Aguarde.");
        }

        try {
            SessionCreateParams.Builder builder = SessionCreateParams.builder()
                    .setMode(sessionMode(request.plan()))
                    .setCustomerEmail(currentUser.getEmail())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(priceId)
                            .setQuantity(1L)
                            .build())
                    .putMetadata("plan", request.plan())
                    .putMetadata("userId", currentUser.getId().toString())
                    .setSuccessUrl(frontendUrl + "/sucesso?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(frontendUrl + "/planos");

            Session session = Session.create(builder.build());

            log.info("Checkout criado para userId={}, plano={}, session={}",
                    currentUser.getId(), request.plan(), session.getId());

            return ResponseEntity.ok(new CheckoutResponse(session.getUrl(), session.getId()));

        } catch (StripeException e) {
            log.error("Erro ao criar checkout Stripe para userId={}: {}", currentUser.getId(), e.getMessage());
            throw new BusinessException("Não foi possível iniciar o pagamento. Tente novamente.");
        }
    }

    // ── Cancelamento ─────────────────────────────────────────────

    @Operation(summary = "Cancelar assinatura recorrente — acesso mantido até fim do período pago")
    @PostMapping("/api/v1/subscriptions/cancel")
    public ResponseEntity<Map<String, String>> cancelSubscription(
            @AuthenticationPrincipal User currentUser
    ) {
        var subscription = subscriptionRepository
                .findActiveByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Nenhuma assinatura ativa encontrada."));

        // Pagamento único (financas sem recorrência) — não há o que cancelar no Stripe
        if (subscription.getStripeSubscriptionId() == null) {
            throw new BusinessException(
                "Sua compra foi única e não possui cobrança recorrente. " +
                "Para solicitar reembolso, entre em contato com o suporte."
            );
        }

        try {
            // Cancela no Stripe ao fim do período atual — aluno mantém acesso até expirar
            com.stripe.model.Subscription stripeSub =
                    com.stripe.model.Subscription.retrieve(subscription.getStripeSubscriptionId());

            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true)
                    .build();
            stripeSub.update(params);

            log.info("Cancelamento agendado no Stripe para userId={}", currentUser.getId());

        } catch (StripeException e) {
            log.error("Erro ao cancelar no Stripe para userId={}: {}", currentUser.getId(), e.getMessage());
            // Mesmo com erro no Stripe, marcamos no nosso banco para consistência
        }

        // Marca como CANCELLED no nosso banco — acesso continua via expiresAt
        subscriptionService.cancel(currentUser.getId());

        return ResponseEntity.ok(Map.of(
                "message", "Assinatura cancelada. Você mantém acesso até o fim do período pago.",
                "status", "CANCELLED"
        ));
    }

    // ── Helpers ──────────────────────────────────────────────────

    private String resolvePriceId(String plan) {
        return switch (plan) {
            case "financas" -> stripePriceFinancas;
            case "combo"    -> stripePriceCombo;
            default         -> stripePriceTribo;
        };
    }

    private SessionCreateParams.Mode sessionMode(String plan) {
        // financas = pagamento único (R$97 avulso)
        // tribo/combo = assinatura recorrente mensal
        return "financas".equals(plan)
                ? SessionCreateParams.Mode.PAYMENT
                : SessionCreateParams.Mode.SUBSCRIPTION;
    }

    // ── DTOs ─────────────────────────────────────────────────────

    public record CheckoutRequest(@NotBlank String plan) {}

    public record CheckoutResponse(String checkoutUrl, String sessionId) {}
}
