package com.tribo.modules.payment.service;

import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.tribo.modules.auth.entity.PasswordResetToken;
import com.tribo.modules.auth.repository.PasswordResetTokenRepository;
import com.tribo.modules.auth.service.SubscriptionService;
import com.tribo.modules.notification.service.EmailService;
import com.tribo.modules.notification.service.NotificationService;
import com.tribo.modules.payment.entity.Subscription.SubscriptionStatus;
import com.tribo.modules.payment.repository.SubscriptionRepository;
import com.tribo.modules.user.entity.User;
import com.tribo.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Processa eventos do Stripe de forma síncrona.
 *
 * Sem @Async — o controller só responde 200 ao Stripe quando o processamento
 * terminou com sucesso. Em caso de falha, o controller retorna 500 e o Stripe
 * faz retry automático com backoff exponencial (até 72h).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final SubscriptionRepository subscriptionRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * Ativado quando o pagamento é aprovado no Stripe.
     *
     * Metadados que precisam estar configurados no Stripe Checkout:
     * - plan: "tribo" | "financas" | "combo"
     */
    @Transactional
    public void handleCheckoutCompleted(Session session, String eventId) {
        String customerEmail = session.getCustomerEmail();
        String customerId = session.getCustomer();
        String subscriptionId = session.getSubscription();
        String plan = session.getMetadata().getOrDefault("plan", "tribo");

        log.info("Pagamento aprovado — email: {}, plan: {}", customerEmail, plan);

        Optional<User> userOpt = userRepository.findByEmail(customerEmail);
        User user;

        if (userOpt.isPresent()) {
            user = userOpt.get();
            user.setStatus(User.AccountStatus.ACTIVE);
            userRepository.save(user);
            emailService.enviarAcessoLiberado(user.getEmail(), user.getName(), plan);
        } else {
            // Novo usuário via Stripe — cria conta com senha bloqueada
            user = User.builder()
                    .name(extractNameFromEmail(customerEmail))
                    .email(customerEmail)
                    .passwordHash("$2a$12$LOCKED_NO_PASSWORD_SET_PLEASE_RESET_THIS_NOW")
                    .role(User.Role.STUDENT)
                    .status(User.AccountStatus.ACTIVE)
                    .build();
            userRepository.save(user);

            // Token 48h para criar a senha (mais tempo que o reset normal de 1h)
            String resetToken = UUID.randomUUID().toString().replace("-", "");
            passwordResetTokenRepository.save(
                PasswordResetToken.builder()
                    .userId(user.getId())
                    .token(resetToken)
                    .expiresAt(OffsetDateTime.now().plusHours(48))
                    .build()
            );

            emailService.enviarBoasVindasNovoPagante(user.getEmail(), user.getName(), plan, resetToken);
            log.info("Novo usuário criado via Stripe: {}", customerEmail);
        }

        subscriptionService.activate(user.getId(), plan, customerId, subscriptionId, eventId);
        log.info("Assinatura ativada para {} — plano: {}", customerEmail, plan);

        notificationService.create(user.getId(), "payment",
                "Pagamento confirmado!",
                "Seu acesso ao plano " + plan + " foi ativado. Boas-vindas à Tribo!",
                Map.of("plan", plan));
    }

    /**
     * Ativado quando o usuário cancela a assinatura no Stripe.
     * O acesso continua até o fim do período pago (expiresAt permanece inalterado).
     */
    @Transactional
    public void handleSubscriptionCancelled(Event event) {
        log.info("Assinatura cancelada — event: {}", event.getId());

        Subscription stripeSubscription = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (stripeSubscription == null) {
            log.warn("Não foi possível desserializar Subscription do evento {}", event.getId());
            return;
        }

        subscriptionRepository.findByStripeSubscriptionId(stripeSubscription.getId())
            .ifPresentOrElse(sub -> {
                sub.setStatus(SubscriptionStatus.CANCELLED);
                sub.setCancelledAt(OffsetDateTime.now());
                subscriptionRepository.save(sub);
                log.info("Assinatura {} marcada como CANCELLED", stripeSubscription.getId());

                userRepository.findById(sub.getUserId()).ifPresent(user -> {
                    emailService.enviarAssinaturaCancelada(user.getEmail(), user.getName());
                    notificationService.create(user.getId(), "payment",
                            "Assinatura cancelada",
                            "Sua assinatura foi cancelada. Você mantém o acesso até o fim do período pago.",
                            Map.of());
                });
            }, () -> log.warn("Assinatura não encontrada para stripeSubscriptionId={}",
                    stripeSubscription.getId()));
    }

    /**
     * Ativado quando o plano da assinatura é alterado no Stripe (upgrade/downgrade).
     * Atualiza o plano no banco e invalida o cache de assinatura.
     */
    @Transactional
    public void handleSubscriptionUpdated(Event event) {
        log.info("Assinatura atualizada — event: {}", event.getId());

        Subscription stripeSubscription = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (stripeSubscription == null) {
            log.warn("Não foi possível desserializar Subscription do evento {}", event.getId());
            return;
        }

        // Extrai o metadata do plano. O plano deve ser enviado como metadata
        // na criação da Subscription no Stripe, assim como no Checkout.
        String newPlan = null;
        if (stripeSubscription.getMetadata() != null) {
            newPlan = stripeSubscription.getMetadata().get("plan");
        }

        // Se não houver metadata de plano, tenta inferir pelo status da subscription
        String stripeStatus = stripeSubscription.getStatus();

        subscriptionRepository.findByStripeSubscriptionId(stripeSubscription.getId())
            .ifPresentOrElse(sub -> {
                boolean changed = false;

                // Atualiza o plano se veio no metadata e é diferente do atual
                if (newPlan != null && !newPlan.equals(sub.getPlan())) {
                    log.info("Plano atualizado: {} → {} para stripeSubscriptionId={}",
                            sub.getPlan(), newPlan, stripeSubscription.getId());
                    sub.setPlan(newPlan);
                    changed = true;
                }

                // Sincroniza status: se o Stripe marcou como active/past_due/etc
                if ("active".equals(stripeStatus) && sub.getStatus() != SubscriptionStatus.ACTIVE) {
                    sub.setStatus(SubscriptionStatus.ACTIVE);
                    changed = true;
                } else if ("past_due".equals(stripeStatus) || "unpaid".equals(stripeStatus)) {
                    // Mantém ACTIVE por ora — handlePaymentFailed já notifica o usuário.
                    // Após X falhas o Stripe dispara customer.subscription.deleted.
                    log.warn("Assinatura {} com status Stripe: {}", stripeSubscription.getId(), stripeStatus);
                }

                if (changed) {
                    subscriptionRepository.save(sub);
                    // Invalida cache de assinatura para o usuário
                    subscriptionService.evictCache(sub.getUserId());
                }

            }, () -> log.warn("Assinatura não encontrada para stripeSubscriptionId={}",
                    stripeSubscription.getId()));
    }

    /**
     * Ativado quando uma cobrança recorrente falha.
     * Após X tentativas, o Stripe dispara customer.subscription.deleted automaticamente.
     */
    @Transactional
    public void handlePaymentFailed(Event event) {
        log.warn("Pagamento falhou — event: {}", event.getId());

        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (invoice == null) {
            log.warn("Não foi possível desserializar Invoice do evento {}", event.getId());
            return;
        }

        String customerEmail = invoice.getCustomerEmail();
        if (customerEmail == null || customerEmail.isBlank()) {
            log.warn("Invoice {} sem customerEmail", invoice.getId());
            return;
        }

        userRepository.findByEmail(customerEmail).ifPresent(user -> {
            emailService.enviarPagamentoFalhou(user.getEmail(), user.getName());
            notificationService.create(user.getId(), "payment",
                    "Falha no pagamento",
                    "Não conseguimos processar seu pagamento. Por favor, atualize seus dados de cobrança.",
                    Map.of());
        });
    }

    // ── Helpers ──────────────────────────────────────────────────

    private String extractNameFromEmail(String email) {
        String local = email.split("@")[0].replace(".", " ").replace("_", " ");
        String[] parts = local.split(" ");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                name.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1).toLowerCase())
                    .append(" ");
            }
        }
        return name.toString().trim();
    }
}
