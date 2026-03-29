package com.tribo.modules.payment.job;

import com.tribo.modules.notification.service.EmailService;
import com.tribo.modules.payment.entity.Subscription;
import com.tribo.modules.payment.repository.SubscriptionRepository;
import com.tribo.modules.user.entity.User;
import com.tribo.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Job agendado para expirar assinaturas automaticamente.
 *
 * Roda a cada hora e verifica assinaturas ACTIVE com expiresAt no passado.
 * Marca como EXPIRED e notifica o aluno por email.
 *
 * Habilitar no TriboApplication com @EnableScheduling.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpirationJob {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    /**
     * Roda todo dia às 03:00 da manhã.
     * Cron: segundo minuto hora dia mês dia-semana
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void expirarAssinaturasVencidas() {
        log.info("Iniciando verificação de assinaturas expiradas...");

        List<Subscription> expiradas = subscriptionRepository
                .findExpiredSubscriptions(OffsetDateTime.now());

        if (expiradas.isEmpty()) {
            log.info("Nenhuma assinatura expirada encontrada.");
            return;
        }

        log.info("Expirando {} assinatura(s)...", expiradas.size());

        for (Subscription sub : expiradas) {
            try {
                sub.setStatus(Subscription.SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(sub);

                // Notifica o aluno por email
                userRepository.findById(sub.getUserId()).ifPresent(user -> {
                    emailService.enviarAssinaturaCancelada(user.getEmail(), user.getName());
                    log.info("Assinatura expirada para userId={}", sub.getUserId());
                });

            } catch (Exception e) {
                log.error("Erro ao expirar assinatura id={}: {}", sub.getId(), e.getMessage());
            }
        }

        log.info("Verificação concluída. {} assinatura(s) expirada(s).", expiradas.size());
    }

    /**
     * Roda todo dia às 10:00 — notifica alunos que vão expirar em 3 dias.
     */
    @Scheduled(cron = "0 0 10 * * *")
    @Transactional(readOnly = true)
    public void notificarProximasExpirações() {
        OffsetDateTime em3dias = OffsetDateTime.now().plusDays(3);
        OffsetDateTime agora = OffsetDateTime.now();

        List<Subscription> aVencer = subscriptionRepository
                .findExpiringBetween(agora, em3dias);

        for (Subscription sub : aVencer) {
            userRepository.findById(sub.getUserId()).ifPresent(user -> {
                emailService.enviarPagamentoFalhou(user.getEmail(), user.getName());
                log.info("Notificação de vencimento enviada para userId={}", sub.getUserId());
            });
        }

        if (!aVencer.isEmpty()) {
            log.info("{} aluno(s) notificado(s) sobre vencimento em 3 dias.", aVencer.size());
        }
    }
}
