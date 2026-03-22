package com.tribo.modules.payment.service;

import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.tribo.modules.auth.service.SubscriptionService;
import com.tribo.modules.user.entity.User;
import com.tribo.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Processa eventos do Stripe de forma assíncrona.
 *
 * @Async garante que o controller responda ao Stripe em < 2s,
 * enquanto o processamento acontece em background.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    /**
     * Ativado quando o pagamento é aprovado no Stripe.
     *
     * O Stripe envia o email do cliente na sessão — usamos isso
     * para encontrar ou criar o usuário na nossa base.
     *
     * Metadados que precisam ser configurados no checkout do Stripe:
     * - plan: "tribo" | "financas" | "combo"
     */
    @Async
    @Transactional
    public void handleCheckoutCompleted(Session session, String eventId) {
        String customerEmail = session.getCustomerEmail();
        String customerId = session.getCustomer();
        String subscriptionId = session.getSubscription();
        String plan = session.getMetadata().getOrDefault("plan", "tribo");

        log.info("Pagamento aprovado — email: {}, plan: {}", customerEmail, plan);

        // Busca ou cria o usuário
        Optional<User> userOpt = userRepository.findByEmail(customerEmail);
        User user;

        if (userOpt.isPresent()) {
            // Usuário já existe — apenas ativa a assinatura
            user = userOpt.get();
            user.setStatus(User.AccountStatus.ACTIVE);
            userRepository.save(user);
        } else {
            // Novo usuário — cria conta com senha temporária
            // O usuário receberá um email para definir a senha
            user = User.builder()
                    .name(extractNameFromEmail(customerEmail))
                    .email(customerEmail)
                    .passwordHash("$2a$12$PLACEHOLDER") // senha bloqueada até reset
                    .role(User.Role.STUDENT)
                    .status(User.AccountStatus.ACTIVE)
                    .build();
            userRepository.save(user);

            // TODO: enviar email de boas-vindas com link para definir senha
            log.info("Novo usuário criado via Stripe: {}", customerEmail);
        }

        // Ativa a assinatura
        subscriptionService.activate(
                user.getId(),
                plan,
                customerId,
                subscriptionId,
                eventId
        );

        log.info("Assinatura ativada para {} — plano: {}", customerEmail, plan);
    }

    /**
     * Ativado quando o usuário cancela a assinatura no Stripe.
     */
    @Async
    @Transactional
    public void handleSubscriptionCancelled(Event event) {
        log.info("Assinatura cancelada — event: {}", event.getId());
        // TODO: buscar usuário pelo stripeSubscriptionId e marcar assinatura como CANCELLED
        // O acesso continua até o fim do período pago (expiresAt)
    }

    /**
     * Ativado quando uma cobrança recorrente falha.
     */
    @Async
    public void handlePaymentFailed(Event event) {
        log.warn("Pagamento falhou — event: {}", event.getId());
        // TODO: enviar email de aviso ao usuário
        // Após X tentativas falhas, o Stripe cancela automaticamente
    }

    private String extractNameFromEmail(String email) {
        // "joao.silva@gmail.com" → "joao.silva"
        return email.split("@")[0].replace(".", " ");
    }
}
