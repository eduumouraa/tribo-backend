package com.tribo.modules.payment.service;

import com.tribo.modules.auth.service.SubscriptionService;
import com.tribo.modules.payment.entity.Subscription;
import com.tribo.modules.payment.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionService — testes unitários")
class SubscriptionServiceTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @InjectMocks SubscriptionService subscriptionService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    private Subscription activeSubscription(String plan) {
        return Subscription.builder()
                .userId(userId)
                .plan(plan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .provider("stripe")
                .build();
    }

    // ── hasActiveSubscription ────────────────────────────────────

    @Test
    @DisplayName("hasActiveSubscription: retorna true com assinatura ACTIVE sem expiração")
    void hasActive_comAssinaturaAtiva() {
        when(subscriptionRepository.findActiveByUserId(userId))
                .thenReturn(Optional.of(activeSubscription("tribo")));

        assertThat(subscriptionService.hasActiveSubscription(userId)).isTrue();
    }

    @Test
    @DisplayName("hasActiveSubscription: retorna false sem assinatura")
    void hasActive_semAssinatura() {
        when(subscriptionRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());

        assertThat(subscriptionService.hasActiveSubscription(userId)).isFalse();
    }

    @Test
    @DisplayName("hasActiveSubscription: retorna false com assinatura EXPIRADA")
    void hasActive_assinaturaExpirada() {
        var expired = Subscription.builder()
                .userId(userId)
                .plan("tribo")
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .expiresAt(OffsetDateTime.now().minusDays(1)) // expirou ontem
                .provider("stripe")
                .build();

        when(subscriptionRepository.findActiveByUserId(userId)).thenReturn(Optional.of(expired));

        assertThat(subscriptionService.hasActiveSubscription(userId)).isFalse();
    }

    // ── hasAccessToCourse — controle por plano ───────────────────

    @Test
    @DisplayName("hasAccessToCourse: plano combo acessa qualquer curso")
    void planAccess_comboAcessaTudo() {
        when(subscriptionRepository.findActiveByUserId(userId))
                .thenReturn(Optional.of(activeSubscription("combo")));

        assertThat(subscriptionService.hasAccessToCourse(userId, "tribo")).isTrue();
        assertThat(subscriptionService.hasAccessToCourse(userId, "financas")).isTrue();
    }

    @Test
    @DisplayName("hasAccessToCourse: plano tribo NÃO acessa curso financas")
    void planAccess_triboNaoAcessaFinancas() {
        when(subscriptionRepository.findActiveByUserId(userId))
                .thenReturn(Optional.of(activeSubscription("tribo")));

        assertThat(subscriptionService.hasAccessToCourse(userId, "financas")).isFalse();
    }

    @Test
    @DisplayName("hasAccessToCourse: plano financas NÃO acessa curso tribo")
    void planAccess_financasNaoAcessaTribo() {
        when(subscriptionRepository.findActiveByUserId(userId))
                .thenReturn(Optional.of(activeSubscription("financas")));

        assertThat(subscriptionService.hasAccessToCourse(userId, "tribo")).isFalse();
    }

    @Test
    @DisplayName("hasAccessToCourse: plano tribo acessa curso tribo")
    void planAccess_triboAcessaTribo() {
        when(subscriptionRepository.findActiveByUserId(userId))
                .thenReturn(Optional.of(activeSubscription("tribo")));

        assertThat(subscriptionService.hasAccessToCourse(userId, "tribo")).isTrue();
    }

    @Test
    @DisplayName("hasAccessToCourse: curso free sem assinatura — acesso liberado")
    void planAccess_cursoFreeAcessivel() {
        // Sem necessidade de buscar no banco para cursos free
        assertThat(subscriptionService.hasAccessToCourse(userId, "free")).isTrue();
        assertThat(subscriptionService.hasAccessToCourse(userId, null)).isTrue();
        verify(subscriptionRepository, never()).findActiveByUserId(any());
    }

    @Test
    @DisplayName("hasAccessToCourse: sem assinatura ativa, acesso negado")
    void planAccess_semAssinaturaAcessoNegado() {
        when(subscriptionRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());

        assertThat(subscriptionService.hasAccessToCourse(userId, "tribo")).isFalse();
    }

    // ── activate ─────────────────────────────────────────────────

    @Test
    @DisplayName("activate: cria nova assinatura cancelando a anterior")
    void activate_cancelaAnteriorECriaNova() {
        var existing = activeSubscription("tribo");
        when(subscriptionRepository.findActiveByUserId(userId)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        subscriptionService.activate(userId, "combo", "cus_123", "sub_456", "evt_789");

        // Cancela a anterior
        verify(subscriptionRepository, times(2)).save(any());
        assertThat(existing.getStatus()).isEqualTo(Subscription.SubscriptionStatus.CANCELLED);
    }

    // ── cancel ───────────────────────────────────────────────────

    @Test
    @DisplayName("cancel: marca assinatura como CANCELLED")
    void cancel_marcaComoCancelled() {
        var sub = activeSubscription("tribo");
        when(subscriptionRepository.findActiveByUserId(userId)).thenReturn(Optional.of(sub));

        subscriptionService.cancel(userId);

        assertThat(sub.getStatus()).isEqualTo(Subscription.SubscriptionStatus.CANCELLED);
        verify(subscriptionRepository).save(sub);
    }

    @Test
    @DisplayName("cancel: não lança exceção se não há assinatura ativa")
    void cancel_semAssinaturaNaoLancaExcecao() {
        when(subscriptionRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());

        assertThatCode(() -> subscriptionService.cancel(userId))
                .doesNotThrowAnyException();
    }
}
